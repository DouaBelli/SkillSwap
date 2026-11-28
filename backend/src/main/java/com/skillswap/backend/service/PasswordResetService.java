package com.skillswap.backend.service;

import com.skillswap.backend.entity.EmailVerification;
import com.skillswap.backend.entity.User;
import com.skillswap.backend.repository.EmailVerificationRepository;
import com.skillswap.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final String PURPOSE = "PASSWORD_RESET";
    private static final int MAX_ATTEMPTS = 3;
    private static final long RESEND_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final PasswordEncoder encoder;
    private final MailService mailService;

    public PasswordResetService(UserRepository users, EmailVerificationRepository verifications,
                                PasswordEncoder encoder, MailService mailService) {
        this.users = users;
        this.verifications = verifications;
        this.encoder = encoder;
        this.mailService = mailService;
    }

    // Same answer whether or not the email exists, so nobody can find out who has an account.
    @Transactional
    public void forgot(String emailRaw) {
        User user = users.findByUniversityEmail(emailRaw.trim().toLowerCase()).orElse(null);
        if (user == null || !user.isVerified()) {
            return;
        }
        Optional<EmailVerification> last = verifications
                .findFirstByUserAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user, PURPOSE);
        if (last.isPresent()) {
            long age = Duration.between(last.get().getCreatedAt(), Instant.now()).getSeconds();
            if (age < RESEND_SECONDS) {
                throw bad("Please wait " + (RESEND_SECONDS - age) + " seconds before asking for a new code.");
            }
        }
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        EmailVerification ev = new EmailVerification();
        ev.setUser(user);
        ev.setPurpose(PURPOSE);
        ev.setOtpHash(encoder.encode(code));
        verifications.save(ev);
        mailService.sendCode(user.getUniversityEmail(), code);
    }

    // noRollbackFor: the wrong-tries counter must be saved even though we return an error
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void reset(String emailRaw, String code, String newPassword) {
        User user = users.findByUniversityEmail(emailRaw.trim().toLowerCase())
                .orElseThrow(() -> bad("Wrong code or email."));
        EmailVerification ev = verifications
                .findFirstByUserAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user, PURPOSE)
                .orElseThrow(() -> bad("No code found. Ask for a new code."));

        if (Instant.now().isAfter(ev.getExpiresAt()) || ev.getAttempts() >= MAX_ATTEMPTS) {
            throw bad("The code expired or has too many wrong tries. Ask for a new code.");
        }
        if (!encoder.matches(code, ev.getOtpHash())) {
            ev.setAttempts((short) (ev.getAttempts() + 1));
            int left = MAX_ATTEMPTS - ev.getAttempts();
            throw bad(left > 0 ? "Wrong code, " + left + " tries left."
                               : "Wrong code. No tries left. Ask for a new code.");
        }
        ev.setUsedAt(Instant.now());
        user.setPasswordHash(encoder.encode(newPassword));
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}