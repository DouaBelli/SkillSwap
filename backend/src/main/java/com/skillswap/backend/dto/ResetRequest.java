package com.skillswap.backend.dto;

import jakarta.validation.constraints.*;

public record ResetRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String newPassword) {}