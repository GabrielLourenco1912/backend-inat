package dev.lourencogabriel.inatead.notification.dto;

import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class NotificationRecipientDtos {

    private NotificationRecipientDtos() {
    }

    public record DeliveryRequest(
            @NotNull NotificationDeliveryStatus deliveryStatus,
            @Size(max = 500) String failureReason
    ) {
    }

    public record Response(
            String id,
            String notificationId,
            String recipientUserId,
            NotificationChannel channel,
            NotificationDeliveryStatus deliveryStatus,
            Instant sentAt,
            Instant deliveredAt,
            Instant readAt,
            String failureReason,
            String notificationType,
            String title,
            String message,
            String actionUrl,
            String contextType,
            String contextId,
            NotificationPriority priority,
            Instant scheduledAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record UnreadCount(long count) {
    }
}
