package com.example.ems.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.ems.config.MailAsyncConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private static final String SENDER = "notifications@example.test";
    private static final String REGISTERED_EMAIL = "asha.rao@example.test";
    private static final Instant LOGIN_TIME = Instant.parse("2026-10-15T20:45:30Z");

    private JavaMailSender mailSender;

    @BeforeEach
    void setup() {
        mailSender = mock(JavaMailSender.class);
    }

    @Test
    void sendsRequestedTemplateToRegisteredEmailUsingIndianStandardTime() {
        EmailService service = new EmailService(mailSender, SENDER, true);

        service.sendSuccessfulLoginEmail(REGISTERED_EMAIL, "Asha Rao", LOGIN_TIME);

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();

        assertAll(
                () -> assertArrayEquals(new String[]{REGISTERED_EMAIL}, message.getTo()),
                () -> assertEquals(SENDER, message.getFrom()),
                () -> assertEquals("Successful Login Alert - Employee Management System", message.getSubject()),
                () -> assertNotNull(message.getText())
        );
        String body = message.getText();
        assertAll(
                () -> assertTrue(body.contains("Hello Asha Rao,")),
                () -> assertTrue(body.contains(
                        "You have successfully logged in to your Employee Management System account.")),
                () -> assertTrue(body.contains("Login Details:")),
                () -> assertTrue(body.contains("- Status: Successful")),
                () -> assertTrue(body.contains("- Date: 16 Oct 2026")),
                () -> assertTrue(body.contains("- Time: 02:15:30 AM IST")),
                () -> assertTrue(body.contains("- Application: Employee Management System")),
                () -> assertTrue(body.contains(
                        "If you did not perform this login, please contact your system administrator immediately.")),
                () -> assertTrue(body.contains("Regards,")),
                () -> assertTrue(body.contains("Employee Management System Team"))
        );
        verifyNoMoreInteractions(mailSender);
    }

    @Test
    void disabledNotificationsDoNotContactSmtp() {
        EmailService service = new EmailService(mailSender, SENDER, false);

        service.sendSuccessfulLoginEmail(REGISTERED_EMAIL, "Asha Rao", LOGIN_TIME);

        verifyNoInteractions(mailSender);
    }

    @Test
    void missingSenderDoesNotContactSmtp() {
        EmailService service = new EmailService(mailSender, "", true);

        assertDoesNotThrow(() ->
                service.sendSuccessfulLoginEmail(REGISTERED_EMAIL, "Asha Rao", LOGIN_TIME));

        verifyNoInteractions(mailSender);
    }

    @Test
    void whitespaceSenderDoesNotContactSmtp() {
        EmailService service = new EmailService(mailSender, "  ", true);

        assertDoesNotThrow(() ->
                service.sendSuccessfulLoginEmail(REGISTERED_EMAIL, "Asha Rao", LOGIN_TIME));

        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailureIsHandledWithoutLoggingRecipientOrSensitiveExceptionDetails() {
        String sensitiveDetail = "smtp-password=private-value token=private-token " + REGISTERED_EMAIL;
        doThrow(new MailSendException(sensitiveDetail))
                .when(mailSender).send(any(SimpleMailMessage.class));
        EmailService service = new EmailService(mailSender, SENDER, true);
        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertDoesNotThrow(() ->
                    service.sendSuccessfulLoginEmail(REGISTERED_EMAIL, "Asha Rao", LOGIN_TIME));

            verify(mailSender).send(any(SimpleMailMessage.class));
            assertFalse(appender.list.isEmpty(), "SMTP failures must be logged");
            for (ILoggingEvent event : appender.list) {
                assertAll(
                        () -> assertFalse(event.getFormattedMessage().contains(REGISTERED_EMAIL)),
                        () -> assertFalse(event.getFormattedMessage().contains("private-value")),
                        () -> assertFalse(event.getFormattedMessage().contains("private-token")),
                        () -> assertNull(event.getThrowableProxy(),
                                "SMTP exception messages and stack traces can expose credentials")
                );
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void springProxyReturnsBeforeMailSendCompletesOnDedicatedExecutor() throws Exception {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        CountDownLatch sendFinished = new CountDownLatch(1);
        AtomicReference<Thread> senderThread = new AtomicReference<>();
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        doAnswer(invocation -> {
            senderThread.set(Thread.currentThread());
            sendStarted.countDown();
            try {
                if (!releaseSend.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test mail sender was not released");
                }
            } finally {
                sendFinished.countDown();
            }
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        ExecutorService caller = Executors.newSingleThreadExecutor();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncTestConfig.class, MailAsyncConfig.class);
            context.registerBean(JavaMailSender.class, () -> mailSender);
            context.registerBean(EmailService.class, () -> new EmailService(mailSender, SENDER, true));
            context.refresh();
            EmailService service = context.getBean(EmailService.class);
            ThreadPoolTaskExecutor executor = context.getBean("loginAlertExecutor", ThreadPoolTaskExecutor.class);

            try {
                caller.submit(() -> {
                    callerThread.set(Thread.currentThread());
                    service.sendSuccessfulLoginEmail(REGISTERED_EMAIL, "Asha Rao", LOGIN_TIME);
                }).get(2, TimeUnit.SECONDS);

                assertTrue(sendStarted.await(2, TimeUnit.SECONDS), "The async worker must start sending");
                assertEquals(1, sendFinished.getCount(), "Mail must still be blocked after the caller returns");
                assertNotSame(callerThread.get(), senderThread.get());
                assertTrue(senderThread.get().getName().startsWith(executor.getThreadNamePrefix()));
            } finally {
                releaseSend.countDown();
            }

            assertTrue(sendFinished.await(2, TimeUnit.SECONDS), "The mail worker must finish after release");
            verify(mailSender).send(any(SimpleMailMessage.class));
        } finally {
            releaseSend.countDown();
            caller.shutdownNow();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncTestConfig {
    }
}
