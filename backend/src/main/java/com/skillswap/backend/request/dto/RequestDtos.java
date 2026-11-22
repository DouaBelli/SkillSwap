package com.skillswap.backend.request.dto;

import com.skillswap.backend.request.RequestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/** Request and response bodies of the volunteering flow API. */
public final class RequestDtos {

    private RequestDtos() {
    }

    /** POST /api/requests */
    public record CreateRequestBody(
            @NotNull(message = "Choose an offer.") Integer offerId,
            @Size(max = 1000, message = "The message can be at most 1000 characters.") String message) {
    }

    /** POST /api/requests/{id}/reject (reason is optional) */
    public record RejectBody(
            @Size(max = 500, message = "The reason can be at most 500 characters.") String reason) {
    }

    /** POST /api/requests/{id}/cancel (reason is required) */
    public record CancelBody(
            @NotBlank(message = "Please give a reason for cancelling.")
            @Size(max = 500, message = "The reason can be at most 500 characters.") String reason) {
    }

    /** POST /api/requests/{id}/confirm (stars are optional) */
    public record ConfirmBody(
            @Min(value = 1, message = "The rating must be from 1 to 5 stars.")
            @Max(value = 5, message = "The rating must be from 1 to 5 stars.") Integer rating) {
    }

    /**
     * One request as shown on the My activity screen.
     *
     * role    = "REQUESTER" (in the Sent tab) or "VOLUNTEER" (in the Received tab).
     * rating  = only shown to the student who gave it; volunteers never see single ratings (NFR-7).
     * actions = the buttons this user may press now: ACCEPT, REJECT, PERFORM, CONFIRM, CANCEL.
     */
    public record RequestView(
            Integer requestId,
            Integer offerId,
            String offerTitle,
            Integer volunteerId,
            String volunteerName,
            Integer requesterId,
            String requesterName,
            String message,
            RequestStatus status,
            Integer rating,
            String rejectReason,
            String cancelReason,
            OffsetDateTime createdAt,
            OffsetDateTime acceptedAt,
            OffsetDateTime performedAt,
            OffsetDateTime confirmedAt,
            String role,
            List<String> actions) {
    }

    /** Returned by every action so the screen can show a success message (NFR-2). */
    public record ActionResult(String message, RequestView request) {
    }

    /** GET /api/requests/waiting-count: number shown on the My activity badge. */
    public record WaitingCount(long count) {
    }

    /** GET /api/credits/me */
    public record CreditTotal(long totalCredits) {
    }

    /** Error body: { "message": "..." } */
    public record ErrorBody(String message) {
    }
}
