package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.mail.EmailDeliveryException;
import dev.lourencogabriel.inatead.mail.EmailLinkResolver;
import dev.lourencogabriel.inatead.mail.EmailMessage;
import dev.lourencogabriel.inatead.mail.EmailSender;
import dev.lourencogabriel.inatead.mail.EmailTemplateRenderer;
import dev.lourencogabriel.inatead.notification.model.Notification;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationEmailDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            NotificationEmailDeliveryService.class
    );

    private final NotificationRecipientRepository recipientRepository;
    private final EmailSender emailSender;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final EmailLinkResolver emailLinkResolver;
    private final Clock clock;

    @Transactional
    public void deliver(String recipientId) {
        NotificationRecipient recipient = this.recipientRepository
                .findByIdForDelivery(recipientId)
                .orElse(null);
        if (!isEligible(recipient)) {
            return;
        }
        if (recipient.getRecipientUser().getStatus() != UserAccountStatus.ACTIVE) {
            recipient.setDeliveryStatus(NotificationDeliveryStatus.CANCELLED);
            recipient.setFailureReason(
                    "Email delivery cancelled because the recipient account is not active"
            );
            this.recipientRepository.save(recipient);
            return;
        }

        try {
            Notification notification = recipient.getNotification();
            String actionUrl = this.emailLinkResolver.resolve(notification.getActionUrl());
            String textBody = buildTextBody(recipient, actionUrl);
            String htmlBody = this.emailTemplateRenderer.render(
                    "notification",
                    templateModel(recipient, actionUrl)
            );
            this.emailSender.send(new EmailMessage(
                    recipient.getRecipientUser().getEmail(),
                    notification.getTitle(),
                    textBody,
                    htmlBody
            ));
            Instant now = Instant.now(this.clock);
            recipient.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            recipient.setSentAt(now);
            recipient.setFailureReason(null);
        } catch (EmailDeliveryException exception) {
            recipient.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
            recipient.setFailureReason(exception.getMessage());
            LOGGER.warn("Email notification delivery failed for recipient {}", recipientId);
        }

        this.recipientRepository.save(recipient);
    }

    private boolean isEligible(NotificationRecipient recipient) {
        if (recipient == null
                || recipient.getChannel() != NotificationChannel.EMAIL
                || recipient.getDeliveryStatus() != NotificationDeliveryStatus.PENDING) {
            return false;
        }
        Instant now = Instant.now(this.clock);
        Notification notification = recipient.getNotification();
        return (notification.getScheduledAt() == null
                || !notification.getScheduledAt().isAfter(now))
                && (notification.getExpiresAt() == null
                || notification.getExpiresAt().isAfter(now));
    }

    private String buildTextBody(NotificationRecipient recipient, String actionUrl) {
        Notification notification = recipient.getNotification();
        StringBuilder body = new StringBuilder()
                .append("Olá, ")
                .append(recipient.getRecipientUser().getName())
                .append("!\n\n")
                .append(notification.getMessage());
        if (actionUrl != null) {
            body.append("\n\nAcesse: ").append(actionUrl);
        }
        body.append("\n\nINAT EAD");
        return body.toString();
    }

    private Map<String, Object> templateModel(
            NotificationRecipient recipient,
            String actionUrl
    ) {
        Notification notification = recipient.getNotification();
        Map<String, Object> model = new HashMap<>();
        model.put("subject", notification.getTitle());
        model.put("preheader", summarize(notification.getMessage()));
        model.put("recipientName", recipient.getRecipientUser().getName());
        model.put("message", notification.getMessage());
        model.put("actionUrl", actionUrl);
        return model;
    }

    private String summarize(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 140
                ? normalized
                : normalized.substring(0, 137) + "...";
    }
}
