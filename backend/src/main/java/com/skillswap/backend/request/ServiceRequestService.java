package com.skillswap.backend.request;

import com.skillswap.backend.request.RequestReadDao.OfferInfo;
import com.skillswap.backend.request.RequestReadDao.RequestRow;
import com.skillswap.backend.request.dto.RequestDtos.RequestView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 *   student requests  -> PENDING
 *   volunteer accepts -> ACCEPTED        (or rejects -> REJECTED)
 *   volunteer presses Service performed -> PERFORMED
 *   student confirms  -> CONFIRMED + exactly 1 credit for the volunteer
 *   either side may cancel (with a reason) before CONFIRMED -> CANCELLED
 * Every method takes the id of the logged-in user and checks that this user
 * is the right person for the step.
 */
@Service
public class ServiceRequestService {

    private static final EnumSet<RequestStatus> OPEN = EnumSet.of(
            RequestStatus.PENDING, RequestStatus.ACCEPTED, RequestStatus.PERFORMED);

    private final ServiceRequestRepository requests;
    private final CreditTransactionRepository credits;
    private final RequestReadDao dao;

    public ServiceRequestService(ServiceRequestRepository requests,
                                 CreditTransactionRepository credits,
                                 RequestReadDao dao) {
        this.requests = requests;
        this.credits = credits;
        this.dao = dao;
    }


    @Transactional
    public ServiceRequest create(Integer userId, Integer offerId, String message) {
        requireActiveStudent(userId);
        if (offerId == null) {
            throw ApiException.badRequest("Choose an offer.");
        }
        OfferInfo offer = dao.findOffer(offerId)
                .orElseThrow(() -> ApiException.notFound("This offer does not exist."));
        if (!offer.isActive()) {
            throw ApiException.conflict("This offer is not available any more.");
        }
        if (offer.volunteerId().equals(userId)) {
            throw ApiException.badRequest("You cannot request help from your own offer.");
        }
        if (requests.existsByOfferIdAndRequesterIdAndStatusIn(offerId, userId, OPEN)) {
            throw ApiException.conflict("You already have an open request for this offer.");
        }
        String cleanMessage = blankToNull(message);
        if (cleanMessage != null && cleanMessage.length() > 1000) {
            throw ApiException.badRequest("The message can be at most 1000 characters.");
        }
        return requests.save(new ServiceRequest(offerId, userId, cleanMessage));
    }


    @Transactional
    public ServiceRequest accept(Integer userId, Integer requestId) {
        requireActiveStudent(userId);
        ServiceRequest r = lock(requestId);
        requireVolunteer(userId, r, "accept");
        requireStatus(r, RequestStatus.PENDING, "Only a pending request can be accepted.");
        r.setStatus(RequestStatus.ACCEPTED);
        r.setAcceptedAt(OffsetDateTime.now());
        return requests.save(r);
    }

    @Transactional
    public ServiceRequest reject(Integer userId, Integer requestId, String reason) {
        requireActiveStudent(userId);
        ServiceRequest r = lock(requestId);
        requireVolunteer(userId, r, "reject");
        requireStatus(r, RequestStatus.PENDING, "Only a pending request can be rejected.");
        r.setStatus(RequestStatus.REJECTED);
        r.setRejectReason(blankToNull(reason));
        return requests.save(r);
    }

    @Transactional
    public ServiceRequest markPerformed(Integer userId, Integer requestId) {
        requireActiveStudent(userId);
        ServiceRequest r = lock(requestId);
        requireVolunteer(userId, r, "mark as performed");
        requireStatus(r, RequestStatus.ACCEPTED, "Only an accepted request can be marked as performed.");
        r.setStatus(RequestStatus.PERFORMED);
        r.setPerformedAt(OffsetDateTime.now());
        return requests.save(r);
    }


    @Transactional
    public ServiceRequest confirm(Integer userId, Integer requestId, Integer rating) {
        requireActiveStudent(userId);
        ServiceRequest r = lock(requestId);
        if (!r.getRequesterId().equals(userId)) {
            throw ApiException.forbidden("Only the student who asked for help can confirm it.");
        }
        if (r.getStatus() == RequestStatus.CONFIRMED) {
            throw ApiException.conflict("This service is already confirmed.");
        }
        requireStatus(r, RequestStatus.PERFORMED,
                "You can confirm only after the volunteer marks the service as performed.");
        if (rating != null && (rating < 1 || rating > 5)) {
            throw ApiException.badRequest("The rating must be from 1 to 5 stars.");
        }

        r.setStatus(RequestStatus.CONFIRMED);
        r.setConfirmedAt(OffsetDateTime.now());
        r.setRating(rating == null ? null : rating.shortValue());
        ServiceRequest saved = requests.save(r);

        // Exactly one credit per request. The row lock above stops two confirms running
        // at once, and UNIQUE(request_id) in the database is the last safety net.
        if (!credits.existsByRequestId(saved.getId())) {
            credits.save(new CreditTransaction(saved.getId()));
        }
        return saved;
    }

    @Transactional
    public ServiceRequest cancel(Integer userId, Integer requestId, String reason) {
        ServiceRequest r = lock(requestId);
        Integer volunteerId = volunteerOf(r);
        if (!userId.equals(r.getRequesterId()) && !userId.equals(volunteerId)) {
            throw ApiException.forbidden("Only the student or the volunteer of this request can cancel it.");
        }
        if (r.getStatus() == RequestStatus.CONFIRMED) {
            throw ApiException.conflict("A confirmed service cannot be cancelled.");
        }
        if (!r.getStatus().isOpen()) {
            throw ApiException.conflict("This request is already closed.");
        }
        String cleanReason = blankToNull(reason);
        if (cleanReason == null) {
            throw ApiException.badRequest("Please give a reason for cancelling.");
        }
        r.setStatus(RequestStatus.CANCELLED);
        r.setCancelReason(cleanReason);
        return requests.save(r);
    }


    @Transactional(readOnly = true)
    public RequestView getView(Integer userId, Integer requestId) {
        RequestRow row = dao.findRow(requestId)
                .orElseThrow(() -> ApiException.notFound("This request does not exist."));
        if (!userId.equals(row.requesterId()) && !userId.equals(row.volunteerId())) {
            throw ApiException.forbidden("You can only see your own requests.");
        }
        return toView(row, userId);
    }

    @Transactional(readOnly = true)
    public List<RequestView> sent(Integer userId) {
        return dao.findSent(userId).stream().map(row -> toView(row, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> received(Integer userId) {
        return dao.findReceived(userId).stream().map(row -> toView(row, userId)).toList();
    }

    @Transactional(readOnly = true)
    public long waitingCount(Integer userId) {
        return dao.countWaiting(userId);
    }

    @Transactional(readOnly = true)
    public long totalCredits(Integer userId) {
        return credits.totalCreditsOf(userId);
    }

    /** The buttons a user may press on a request, so the screen never shows a button that will fail. */
    public static List<String> allowedActions(RequestStatus status, boolean viewerIsVolunteer) {
        List<String> actions = new ArrayList<>();
        if (viewerIsVolunteer) {
            if (status == RequestStatus.PENDING) { actions.add("ACCEPT"); actions.add("REJECT"); }
            if (status == RequestStatus.ACCEPTED) { actions.add("PERFORM"); }
        } else {
            if (status == RequestStatus.PERFORMED) { actions.add("CONFIRM"); }
        }
        if (status.isOpen()) {
            actions.add("CANCEL");
        }
        return actions;
    }


    static RequestView toView(RequestRow row, Integer viewerId) {
        boolean viewerIsVolunteer = viewerId.equals(row.volunteerId());
        return new RequestView(
                row.requestId(), row.offerId(), row.offerTitle(),
                row.volunteerId(), row.volunteerName(),
                row.requesterId(), row.requesterName(),
                row.message(), row.status(),
                viewerIsVolunteer ? null : row.rating(),   // ratings are anonymous to volunteers
                row.rejectReason(), row.cancelReason(),
                row.createdAt(), row.acceptedAt(), row.performedAt(), row.confirmedAt(),
                viewerIsVolunteer ? "VOLUNTEER" : "REQUESTER",
                allowedActions(row.status(), viewerIsVolunteer));
    }

    private ServiceRequest lock(Integer requestId) {
        return requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("This request does not exist."));
    }

    private Integer volunteerOf(ServiceRequest r) {
        return dao.findOffer(r.getOfferId())
                .map(OfferInfo::volunteerId)
                .orElseThrow(() -> ApiException.notFound("The offer of this request does not exist."));
    }

    private void requireVolunteer(Integer userId, ServiceRequest r, String action) {
        if (!userId.equals(volunteerOf(r))) {
            throw ApiException.forbidden("Only the volunteer who posted this offer can " + action + " this request.");
        }
    }

    private static void requireStatus(ServiceRequest r, RequestStatus expected, String message) {
        if (r.getStatus() != expected) {
            throw ApiException.conflict(message + " This request is " + r.getStatus() + ".");
        }
    }

    private void requireActiveStudent(Integer userId) {
        if (!dao.isActiveStudent(userId)) {
            throw ApiException.forbidden("Only active student accounts can do this.");
        }
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
