package com.shopsphere.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression("!'${spring.mail.host:}'.isEmpty()")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender sender;
    private final String from;
    public SmtpEmailService(JavaMailSender sender, @Value("${shopsphere.mail.from}") String from) { this.sender = sender; this.from = from; }

    @Override public void send(String to, String subject, String body) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from); m.setTo(to); m.setSubject(subject); m.setText(body);
        sender.send(m);
    }
}
