package com.skillswap.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class DevMailService implements MailService {
    private static final Logger log = LoggerFactory.getLogger(DevMailService.class);

    @Override
    public void sendCode(String email, String code) {
        log.info("DEV ONLY - verification code for {} is {}", email, code);
    }
}