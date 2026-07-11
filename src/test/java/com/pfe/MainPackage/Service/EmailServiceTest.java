package com.pfe.MainPackage.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The real Gmail SMTP server is never contacted: JavaMailSender is a
 * Mockito mock throughout.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void send_shouldNotCallMailSenderWhenMailIsDisabled() {
        EmailService service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "mailEnabled", false);

        service.send("user@example.com", "Subject", "Body");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void send_shouldDispatchThroughMailSenderWhenEnabled() {
        EmailService service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "mailEnabled", true);

        service.send("user@example.com", "Subject", "Body text");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Subject");
        assertThat(sent.getText()).isEqualTo("Body text");
    }

    @Test
    void send_shouldSetFromHeaderWhenConfigured() {
        EmailService service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@ooredoo-fsm.example");

        service.send("user@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("no-reply@ooredoo-fsm.example");
    }
}
