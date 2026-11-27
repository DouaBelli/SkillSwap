package com.skillswap.backend.controller;

import com.skillswap.backend.dto.*;
import com.skillswap.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, String> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return Map.of("message", "Account created. Enter the 6-digit code sent to your email.");
    }

    @PostMapping("/verify")
    public Map<String, String> verify(@Valid @RequestBody VerifyRequest req) {
        authService.verify(req.email(), req.code());
        return Map.of("message", "Email verified. You can log in now.");
    }

    @PostMapping("/resend")
    public Map<String, String> resend(@Valid @RequestBody ResendRequest req) {
        authService.resend(req.email());
        return Map.of("message", "A new code was sent.");
    }
}