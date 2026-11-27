package com.skillswap.backend.controller;

import com.skillswap.backend.dto.*;
import com.skillswap.backend.service.LoginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class LoginController {
    private final LoginService loginService;

    public LoginController(LoginService loginService) { this.loginService = loginService; }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return loginService.login(req);
    }
}