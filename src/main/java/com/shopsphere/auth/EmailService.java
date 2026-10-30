package com.shopsphere.auth;

public interface EmailService {
    void send(String to, String subject, String body);
}
