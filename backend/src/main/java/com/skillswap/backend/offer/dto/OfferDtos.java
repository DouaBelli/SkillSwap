package com.skillswap.backend.offer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Request and response bodies of the offers API. */
public final class OfferDtos {

    private OfferDtos() {
    }

    /** POST /api/offers and PUT /api/offers/{id} */
    public record OfferBody(
            @NotBlank(message = "Please enter a title.")
            @Size(max = 100, message = "The title can be at most 100 characters.") String title,
            @NotNull(message = "Choose a category.") Integer categoryId,
            @NotBlank(message = "Please describe your offer.")
            @Size(max = 1000, message = "The description can be at most 1000 characters.") String description,
            @NotBlank(message = "Please enter when you are available.")
            @Size(max = 100, message = "The availability can be at most 100 characters.") String availability,
            @NotBlank(message = "Please say how you will help.")
            @Size(max = 100, message = "The help method can be at most 100 characters.") String helpMethod) {
    }

    /**
     * One offer as shown on Browse offers, Offer details and Edit offer.
     * mine = true when the logged-in user owns the offer (the screen then shows Edit and Hide).
     */
    public record OfferView(
            Integer offerId,
            String title,
            String description,
            Integer categoryId,
            String categoryName,
            Integer volunteerId,
            String volunteerName,
            Integer yearOfStudy,
            String departmentName,
            String availability,
            String helpMethod,
            String status,
            OffsetDateTime createdAt,
            boolean mine) {
    }

    /** Returned by create, update and hide so the screen can show a success message. */
    public record OfferResult(String message, OfferView offer) {
    }

    /** A category (for the filter buttons and the Post offer dropdown). */
    public record Option(Integer id, String name) {
    }
}
