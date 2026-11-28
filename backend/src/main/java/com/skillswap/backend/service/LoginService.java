package com.skillswap.backend.service;

import com.skillswap.backend.dto.*;
import com.skillswap.backend.entity.User;
import com.skillswap.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public LoginService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public LoginResponse login(LoginRequest req) {
        User u = users.findByUniversityEmail(req.email().trim().toLowerCase()).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
        }
        if (!u.isVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_VERIFIED");
        }
        if (users.isBlocked(u.getId())) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is blocked.");
}
        return new LoginResponse(jwt.generate(u), u.getId(), u.getFullName(), u.getRole().getName());
    }
}