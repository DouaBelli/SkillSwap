package com.skillswap.backend.dto;

public record LoginResponse(String token, Integer userId, String fullName, String role) {}