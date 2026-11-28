package com.skillswap.backend.dto;

import jakarta.validation.constraints.*;

public record ForgotRequest(@NotBlank @Email String email) {}