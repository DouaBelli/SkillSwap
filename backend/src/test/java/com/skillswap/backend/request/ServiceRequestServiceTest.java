package com.skillswap.backend.request;

import com.skillswap.backend.request.RequestReadDao.OfferInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of the request status flow. The repositories are backed by in-memory maps,
 * so these tests run without a database.
 */
class ServiceRequestServiceTest {

    // Test people
    static final int VOLUNTEER = 1;   // owns offer 10
    static final int STUDENT = 2;
    static final int OTHER = 3;       // an unrelated student
    static final int ADMIN = 99;

    // Test offers
    static final int OFFER = 10;          // ACTIVE, by VOLUNTEER
    static final int HIDDEN_OFFER = 11;   // HIDDEN, by VOLUNTEER

    private final Map<Integer, ServiceRequest> requestStore = new HashMap<>();
    private final List<CreditTransaction> creditStore = new ArrayList<>();
    private ServiceRequestService service;

    @BeforeEach
    void setUp() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        CreditTransactionRepository credits = mock(CreditTransactionRepository.class);
        RequestReadDao dao = mock(RequestReadDao.class);

        when(requests.save(any(ServiceRequest.class))).thenAnswer(inv -> {
            ServiceRequest r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(requestStore.size() + 1);
            }
            requestStore.put(r.getId(), r);
            return r;
        });
        when(requests.findByIdForUpdate(anyInt()))
                .thenAnswer(inv -> Optional.ofNullable(requestStore.get((Integer) inv.getArgument(0))));
        when(requests.existsByOfferIdAndRequesterIdAndStatusIn(anyInt(), anyInt(), anyCollection()))
                .thenAnswer(inv -> {
                    Integer offerId = inv.getArgument(0);
                    Integer requesterId = inv.getArgument(1);
                    Collection<RequestStatus> statuses = inv.getArgument(2);
                    return requestStore.values().stream().anyMatch(r ->
                            r.getOfferId().equals(offerId)
                                    && r.getRequesterId().equals(requesterId)
                                    && statuses.contains(r.getStatus()));
                });

        when(credits.save(any(CreditTransaction.class))).thenAnswer(inv -> {
            creditStore.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(credits.existsByRequestId(anyInt())).thenAnswer(inv ->
                creditStore.stream().anyMatch(c -> c.getRequestId().equals(inv.getArgument(0))));

        when(dao.findOffer(anyInt())).thenAnswer(inv -> {
            int id = inv.getArgument(0);
            if (id == OFFER) return Optional.of(new OfferInfo(OFFER, VOLUNTEER, "ACTIVE", "Excel basics"));
            if (id == HIDDEN_OFFER) return Optional.of(new OfferInfo(HIDDEN_OFFER, VOLUNTEER, "HIDDEN", "Old offer"));
            return Optional.empty();
        });
        when(dao.isActiveStudent(anyInt())).thenAnswer(inv -> !Integer.valueOf(ADMIN).equals(inv.getArgument(0)));

        service = new ServiceRequestService(requests, credits, dao);
    }

    // ------------------------------------------------------------ happy path

    @Test
    void fullFlow_requestAcceptPerformConfirm_givesExactlyOneCredit() {
        ServiceRequest r = service.create(STUDENT, OFFER, "  Can you help me with pivot tables?  ");
        assertEquals(RequestStatus.PENDING, r.getStatus());
        assertEquals("Can you help me with pivot tables?", r.getMessage());

        r = service.accept(VOLUNTEER, r.getId());
        assertEquals(RequestStatus.ACCEPTED, r.getStatus());
        assertNotNull(r.getAcceptedAt());

        r = service.markPerformed(VOLUNTEER, r.getId());
        assertEquals(RequestStatus.PERFORMED, r.getStatus());
        assertNotNull(r.getPerformedAt());

        r = service.confirm(STUDENT, r.getId(), 5);
        assertEquals(RequestStatus.CONFIRMED, r.getStatus());
        assertNotNull(r.getConfirmedAt());
        assertEquals((short) 5, r.getRating());

        assertEquals(1, creditStore.size());
        assertEquals(r.getId(), creditStore.get(0).getRequestId());
        assertEquals(1, creditStore.get(0).getCredits());
    }

    @Test
    void confirmWithoutStars_stillConfirmsAndGivesCredit() {
        ServiceRequest r = performedRequest();
        r = service.confirm(STUDENT, r.getId(), null);
        assertEquals(RequestStatus.CONFIRMED, r.getStatus());
        assertNull(r.getRating());
        assertEquals(1, creditStore.size());
    }

    @Test
    void confirmTwice_isRefused_andCreditIsNotGivenTwice() {
        ServiceRequest r = performedRequest();
        service.confirm(STUDENT, r.getId(), 4);
        assertError(HttpStatus.CONFLICT, () -> service.confirm(STUDENT, r.getId(), 4));
        assertEquals(1, creditStore.size());
    }

    @Test
    void reject_withOptionalReason() {
        ServiceRequest r = service.create(STUDENT, OFFER, null);
        r = service.reject(VOLUNTEER, r.getId(), "No time this week");
        assertEquals(RequestStatus.REJECTED, r.getStatus());
        assertEquals("No time this week", r.getRejectReason());

        ServiceRequest r2 = service.create(STUDENT, OFFER, null);   // allowed again after rejection
        r2 = service.reject(VOLUNTEER, r2.getId(), "   ");
        assertNull(r2.getRejectReason());
    }


    @Test
    void cannotRequestOwnOffer() {
        assertError(HttpStatus.BAD_REQUEST, () -> service.create(VOLUNTEER, OFFER, null));
        assertEquals(0, requestStore.size());
    }

    @Test
    void cannotSendDuplicateWhileEarlierRequestIsOpen() {
        ServiceRequest first = service.create(STUDENT, OFFER, null);
        assertError(HttpStatus.CONFLICT, () -> service.create(STUDENT, OFFER, null));    // PENDING

        service.accept(VOLUNTEER, first.getId());
        assertError(HttpStatus.CONFLICT, () -> service.create(STUDENT, OFFER, null));    // ACCEPTED

        service.markPerformed(VOLUNTEER, first.getId());
        assertError(HttpStatus.CONFLICT, () -> service.create(STUDENT, OFFER, null));    // PERFORMED

        service.confirm(STUDENT, first.getId(), null);
        ServiceRequest again = service.create(STUDENT, OFFER, null);                      // closed -> allowed
        assertEquals(RequestStatus.PENDING, again.getStatus());
    }

    @Test
    void cannotRequestHiddenOrMissingOffer() {
        assertError(HttpStatus.CONFLICT, () -> service.create(STUDENT, HIDDEN_OFFER, null));
        assertError(HttpStatus.NOT_FOUND, () -> service.create(STUDENT, 12345, null));
    }

    @Test
    void administratorCannotRequestHelp() {
        assertError(HttpStatus.FORBIDDEN, () -> service.create(ADMIN, OFFER, null));
    }

    // ------------------------------------------------------------ only the right person

    @Test
    void onlyTheVolunteerCanAcceptRejectAndPerform() {
        ServiceRequest r = service.create(STUDENT, OFFER, null);
        Integer id = r.getId();
        assertError(HttpStatus.FORBIDDEN, () -> service.accept(STUDENT, id));
        assertError(HttpStatus.FORBIDDEN, () -> service.accept(OTHER, id));
        assertError(HttpStatus.FORBIDDEN, () -> service.reject(STUDENT, id, null));

        service.accept(VOLUNTEER, id);
        assertError(HttpStatus.FORBIDDEN, () -> service.markPerformed(STUDENT, id));
        assertError(HttpStatus.FORBIDDEN, () -> service.markPerformed(OTHER, id));
    }

    @Test
    void onlyTheRequesterCanConfirm() {
        ServiceRequest r = performedRequest();
        assertError(HttpStatus.FORBIDDEN, () -> service.confirm(VOLUNTEER, r.getId(), 5));
        assertError(HttpStatus.FORBIDDEN, () -> service.confirm(OTHER, r.getId(), 5));
        assertEquals(0, creditStore.size());
    }

    @Test
    void onlyTheTwoPeopleCanCancel() {
        ServiceRequest r = service.create(STUDENT, OFFER, null);
        assertError(HttpStatus.FORBIDDEN, () -> service.cancel(OTHER, r.getId(), "reason"));
    }

    @Test
    void stepsMustHappenInOrder() {
        ServiceRequest r = service.create(STUDENT, OFFER, null);
        Integer id = r.getId();
        assertError(HttpStatus.CONFLICT, () -> service.markPerformed(VOLUNTEER, id));  // not accepted yet
        assertError(HttpStatus.CONFLICT, () -> service.confirm(STUDENT, id, null));     // not performed yet

        service.accept(VOLUNTEER, id);
        assertError(HttpStatus.CONFLICT, () -> service.accept(VOLUNTEER, id));         // already accepted
        assertError(HttpStatus.CONFLICT, () -> service.reject(VOLUNTEER, id, null));   // too late to reject
        assertError(HttpStatus.CONFLICT, () -> service.confirm(STUDENT, id, null));     // not performed yet
        assertEquals(0, creditStore.size());
    }

    @Test
    void ratingMustBeFrom1To5() {
        ServiceRequest r = performedRequest();
        assertError(HttpStatus.BAD_REQUEST, () -> service.confirm(STUDENT, r.getId(), 0));
        assertError(HttpStatus.BAD_REQUEST, () -> service.confirm(STUDENT, r.getId(), 6));
        assertEquals(RequestStatus.PERFORMED, requestStore.get(r.getId()).getStatus());
        assertEquals(0, creditStore.size());
    }


    @Test
    void eitherSideCanCancelBeforeConfirmed_withReason() {
        ServiceRequest a = service.create(STUDENT, OFFER, null);
        a = service.cancel(STUDENT, a.getId(), "Found help elsewhere");
        assertEquals(RequestStatus.CANCELLED, a.getStatus());
        assertEquals("Found help elsewhere", a.getCancelReason());

        ServiceRequest b = performedRequest();
        b = service.cancel(VOLUNTEER, b.getId(), "Student did not show up");
        assertEquals(RequestStatus.CANCELLED, b.getStatus());
        assertEquals(0, creditStore.size());   // no credit for a cancelled request
    }

    @Test
    void cancelNeedsAReason() {
        ServiceRequest r = service.create(STUDENT, OFFER, null);
        assertError(HttpStatus.BAD_REQUEST, () -> service.cancel(STUDENT, r.getId(), "  "));
        assertError(HttpStatus.BAD_REQUEST, () -> service.cancel(STUDENT, r.getId(), null));
        assertEquals(RequestStatus.PENDING, requestStore.get(r.getId()).getStatus());
    }

    @Test
    void cannotCancelAfterConfirmed_creditAndRatingStay() {
        ServiceRequest r = performedRequest();
        service.confirm(STUDENT, r.getId(), 3);
        assertError(HttpStatus.CONFLICT, () -> service.cancel(STUDENT, r.getId(), "changed my mind"));
        assertError(HttpStatus.CONFLICT, () -> service.cancel(VOLUNTEER, r.getId(), "changed my mind"));
        assertEquals(RequestStatus.CONFIRMED, requestStore.get(r.getId()).getStatus());
        assertEquals((short) 3, requestStore.get(r.getId()).getRating());
        assertEquals(1, creditStore.size());
    }

    @Test
    void closedRequestsCannotMoveAgain() {
        ServiceRequest r = service.create(STUDENT, OFFER, null);
        service.reject(VOLUNTEER, r.getId(), null);
        assertError(HttpStatus.CONFLICT, () -> service.accept(VOLUNTEER, r.getId()));
        assertError(HttpStatus.CONFLICT, () -> service.cancel(STUDENT, r.getId(), "reason"));
    }

    @Test
    void unknownRequestIsNotFound() {
        assertError(HttpStatus.NOT_FOUND, () -> service.accept(VOLUNTEER, 777));
    }

    //buttons shown on My activity

    @Test
    void allowedActionsMatchTheStatus() {
        assertEquals(List.of("ACCEPT", "REJECT", "CANCEL"), ServiceRequestService.allowedActions(RequestStatus.PENDING, true));
        assertEquals(List.of("CANCEL"), ServiceRequestService.allowedActions(RequestStatus.PENDING, false));
        assertEquals(List.of("PERFORM", "CANCEL"), ServiceRequestService.allowedActions(RequestStatus.ACCEPTED, true));
        assertEquals(List.of("CONFIRM", "CANCEL"), ServiceRequestService.allowedActions(RequestStatus.PERFORMED, false));
        assertEquals(List.of("CANCEL"), ServiceRequestService.allowedActions(RequestStatus.PERFORMED, true));
        for (RequestStatus closed : Set.of(RequestStatus.CONFIRMED, RequestStatus.REJECTED, RequestStatus.CANCELLED)) {
            assertEquals(List.of(), ServiceRequestService.allowedActions(closed, true));
            assertEquals(List.of(), ServiceRequestService.allowedActions(closed, false));
        }
    }

    // helpers

    private ServiceRequest performedRequest() {
        ServiceRequest r = service.create(STUDENT, OFFER, null);
        service.accept(VOLUNTEER, r.getId());
        return service.markPerformed(VOLUNTEER, r.getId());
    }

    private static void assertError(HttpStatus expected, Runnable action) {
        ApiException e = assertThrows(ApiException.class, action::run);
        assertEquals(expected, e.getStatus(), e.getMessage());
    }
}
