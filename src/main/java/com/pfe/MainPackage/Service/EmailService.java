package com.pfe.MainPackage.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, String subject, String content) {
        if (!mailEnabled) {
            log.info("Mail disabled (app.mail.enabled=false); skipping send to={}, subject={}", to, subject);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();

        if (StringUtils.hasText(mailFrom)) {
            message.setFrom(mailFrom);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);

        mailSender.send(message);
    }

}