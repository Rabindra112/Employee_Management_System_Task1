package com.example.ems.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
public class EmailService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter LOGIN_DATE =
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH).withZone(IST);
    private static final DateTimeFormatter LOGIN_TIME =
            DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH).withZone(IST);
    private static final String SUBJECT = "Successful Login Alert - Employee Management System";

    private final JavaMailSender mailSender;
    private final String senderAddress;
    private final boolean loginAlertEnabled;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String senderAddress,
                        @Value("${app.mail.login-alert-enabled:true}") boolean loginAlertEnabled) {
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
        this.loginAlertEnabled = loginAlertEnabled;
    }

    @Async("loginAlertExecutor")
    public void sendSuccessfulLoginEmail(String registeredEmail, String fullName, Instant loginTime) {
        if (!loginAlertEnabled) {
            return;
        }
        if (senderAddress.isBlank()) {
            log.warn("Login alert email skipped: MAIL_USERNAME is not configured.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderAddress);
            message.setTo(registeredEmail);
            message.setSubject(SUBJECT);
            message.setText("""
                    Hello %s,
                    
                    You have successfully logged in to your Employee Management System account.
                    
                    Login Details:
                    
                    - Status: Successful
                    - Date: %s
                    - Time: %s IST
                    - Application: Employee Management System
                    
                    If you did not perform this login, please contact your system administrator immediately.
                    
                    Regards,
                    Employee Management System Team
                    """.formatted(fullName, LOGIN_DATE.format(loginTime), LOGIN_TIME.format(loginTime)));
            mailSender.send(message);
            log.info("Login alert email accepted by SMTP server.");
        } catch (RuntimeException exception) {
            // MailException is a RuntimeException. Also isolate unexpected mail preparation failures.
            // Exception messages and stack traces may contain recipients or SMTP credentials.
            log.warn("Login alert email delivery failed ({}).", exception.getClass().getSimpleName());
        }
    }
}
