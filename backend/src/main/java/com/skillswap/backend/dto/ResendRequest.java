package com.skillswap.backend.dto;

import jakarta.validation.constraints.*;

public record ResendRequest(@NotBlank @Email String email) {}