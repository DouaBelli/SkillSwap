package com.skillswap.backend.service;

public interface MailService {
    void sendCode(String email, String code);
}