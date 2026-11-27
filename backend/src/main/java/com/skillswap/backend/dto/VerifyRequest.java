package com.skillswap.backend.dto;

import jakarta.validation.constraints.*;

public record VerifyRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {}