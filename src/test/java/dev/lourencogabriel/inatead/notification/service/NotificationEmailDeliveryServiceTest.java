package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.mail.EmailDeliveryException;
import dev.lourencogabriel.inatead.mail.EmailSender;
import dev.lourencogabriel.inatead.notification.model.Notification;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEmailDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Mock
    private NotificationRecipientRepository recipientRepository;
    @Mock
    private EmailSender emailSender;

    private NotificationEmailDeliveryService service;

    @BeforeEach
    void setUp() {
        this.service = new NotificationEmailDeliveryService(
                this.recipientRepository,
                this.emailSender,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldSendEligibleEmailAndMarkItAsSent() {
        NotificationRecipient recipient = recipient();
        when(this.recipientRepository.findByIdForDelivery("recipient-1"))
                .thenReturn(Optional.of(recipient));

        this.service.deliver("recipient-1");

        verify(this.emailSender).send(
                "recipient@example.com",
                "Activity available",
                "Olá, Recipient!\n\nComplete your activity.\n\nAcesse: /activities/1\n\nINAT EAD"
        );
        assertEquals(NotificationDeliveryStatus.SENT, recipient.getDeliveryStatus());
        assertEquals(NOW, recipient.getSentAt());
        assertNull(recipient.getFailureReason());
        verify(this.recipientRepository).save(recipient);
    }

    @Test
    void shouldRecordFailureWithoutPropagatingSmtpError() {
        NotificationRecipient recipient = recipient();
        when(this.recipientRepository.findByIdForDelivery("recipient-1"))
                .thenReturn(Optional.of(recipient));
        doThrow(new EmailDeliveryException(new IllegalStateException("SMTP unavailable")))
                .when(this.emailSender)
                .send("recipient@example.com", "Activity available", "Olá, Recipient!\n\nComplete your activity.\n\nAcesse: /activities/1\n\nINAT EAD");

        this.service.deliver("recipient-1");

        assertEquals(NotificationDeliveryStatus.FAILED, recipient.getDeliveryStatus());
        assertEquals("Email delivery is temporarily unavailable", recipient.getFailureReason());
        assertNotNull(recipient.getFailureReason());
        verify(this.recipientRepository).save(recipient);
    }

    @Test
    void shouldNotSendScheduledEmailBeforeItsAvailability() {
        NotificationRecipient recipient = recipient();
        recipient.getNotification().setScheduledAt(NOW.plusSeconds(60));
        when(this.recipientRepository.findByIdForDelivery("recipient-1"))
                .thenReturn(Optional.of(recipient));

        this.service.deliver("recipient-1");

        verify(this.emailSender, never()).send(
                "recipient@example.com",
                "Activity available",
                "Olá, Recipient!\n\nComplete your activity.\n\nAcesse: /activities/1\n\nINAT EAD"
        );
        verify(this.recipientRepository, never()).save(recipient);
    }

    @Test
    void shouldCancelQueuedEmailWhenRecipientAccountIsInactive() {
        NotificationRecipient recipient = recipient();
        recipient.getRecipientUser().setStatus(UserAccountStatus.DISABLED);
        when(this.recipientRepository.findByIdForDelivery("recipient-1"))
                .thenReturn(Optional.of(recipient));

        this.service.deliver("recipient-1");

        assertEquals(NotificationDeliveryStatus.CANCELLED, recipient.getDeliveryStatus());
        assertEquals(
                "Email delivery cancelled because the recipient account is not active",
                recipient.getFailureReason()
        );
        verify(this.emailSender, never()).send(
                "recipient@example.com",
                "Activity available",
                "Olá, Recipient!\n\nComplete your activity.\n\nAcesse: /activities/1\n\nINAT EAD"
        );
        verify(this.recipientRepository).save(recipient);
    }

    private NotificationRecipient recipient() {
        User user = new User("Recipient", "recipient@example.com", "hash");
        user.setUlid("user-1");

        Notification notification = new Notification();
        notification.setId("notification-1");
        notification.setTitle("Activity available");
        notification.setMessage("Complete your activity.");
        notification.setActionUrl("/activities/1");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId("recipient-1");
        recipient.setNotification(notification);
        recipient.setRecipientUser(user);
        recipient.setChannel(NotificationChannel.EMAIL);
        recipient.setDeliveryStatus(NotificationDeliveryStatus.PENDING);
        return recipient;
    }
}
