package com.skillswap.backend.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
        @NotBlank @Size(max = 100) String fullName,
        @NotNull Integer departmentId,
        @NotNull @Min(1) @Max(6) Short yearOfStudy) {}