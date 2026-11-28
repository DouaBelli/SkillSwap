package com.skillswap.backend.controller;

import com.skillswap.backend.dto.ForgotRequest;
import com.skillswap.backend.dto.ResetRequest;
import com.skillswap.backend.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot")
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest req) {
        service.forgot(req.email());
        return Map.of("message", "If this email has an account, a code was sent.");
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@Valid @RequestBody ResetRequest req) {
        service.reset(req.email(), req.code(), req.newPassword());
        return Map.of("message", "Password changed. You can log in now.");
    }
}