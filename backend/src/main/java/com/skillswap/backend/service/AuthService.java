package com.skillswap.backend.service;

import com.skillswap.backend.dto.RegisterRequest;
import com.skillswap.backend.entity.*;
import com.skillswap.backend.repository.*;
import org.springframework.beans.factory.annotation.Value;
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
public class AuthService {

    private static final String PURPOSE = "REGISTRATION";
    private static final int MAX_ATTEMPTS = 3;
    private static final long RESEND_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final EmailVerificationRepository verifications;
    private final PasswordEncoder encoder;
    private final MailService mailService;
    private final String emailDomain;

    public AuthService(UserRepository users, RoleRepository roles, DepartmentRepository departments,
                       EmailVerificationRepository verifications, PasswordEncoder encoder,
                       MailService mailService,
                       @Value("${skillswap.email-domain:}") String emailDomain) {
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.verifications = verifications;
        this.encoder = encoder;
        this.mailService = mailService;
        this.emailDomain = emailDomain.trim().toLowerCase();
    }

    @Transactional
    public void register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();

        if (!emailDomain.isEmpty() && !email.endsWith("@" + emailDomain)) {
            throw bad("Please use your university email (@" + emailDomain + ").");
        }
        if (users.existsByUniversityEmail(email)) {
            throw bad("This email is already used.");
        }
        Department department = departments.findById(req.departmentId())
                .orElseThrow(() -> bad("Unknown department."));
        Role studentRole = roles.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("STUDENT role is missing in the database"));

        User user = new User();
        user.setUniversityEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setDepartment(department);
        user.setYearOfStudy(req.yearOfStudy());
        user.setRole(studentRole);
        user.setVerified(false);
        users.save(user);

        issueCode(user);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verify(String emailRaw, String code) {
        User user = findUser(emailRaw);
        if (user.isVerified()) {
            throw bad("This account is already verified. Please log in.");
        }
        EmailVerification ev = verifications
                .findFirstByUserAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user, PURPOSE)
                .orElseThrow(() -> bad("No code found. Press Resend code."));

        if (Instant.now().isAfter(ev.getExpiresAt()) || ev.getAttempts() >= MAX_ATTEMPTS) {
            throw bad("The code expired or has too many wrong tries. Press Resend code.");
        }
        if (!encoder.matches(code, ev.getOtpHash())) {
            ev.setAttempts((short) (ev.getAttempts() + 1));
            int left = MAX_ATTEMPTS - ev.getAttempts();
            throw bad(left > 0 ? "Wrong code, " + left + " tries left."
                               : "Wrong code. No tries left. Press Resend code.");
        }
        ev.setUsedAt(Instant.now());
        user.setVerified(true);
    }

    @Transactional
    public void resend(String emailRaw) {
        User user = findUser(emailRaw);
        if (user.isVerified()) {
            throw bad("This account is already verified. Please log in.");
        }
        Optional<EmailVerification> last = verifications
                .findFirstByUserAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user, PURPOSE);
        if (last.isPresent()) {
            long age = Duration.between(last.get().getCreatedAt(), Instant.now()).getSeconds();
            if (age < RESEND_SECONDS) {
                throw bad("Please wait " + (RESEND_SECONDS - age) + " seconds before asking for a new code.");
            }
        }
        issueCode(user);
    }

    private void issueCode(User user) {
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        EmailVerification ev = new EmailVerification();
        ev.setUser(user);
        ev.setPurpose(PURPOSE);
        ev.setOtpHash(encoder.encode(code));
        verifications.save(ev);
        mailService.sendCode(user.getUniversityEmail(), code);
    }

    private User findUser(String emailRaw) {
        return users.findByUniversityEmail(emailRaw.trim().toLowerCase())
                .orElseThrow(() -> bad("No account with this email."));
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}