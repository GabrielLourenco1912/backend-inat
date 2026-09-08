package dev.lourencogabriel.inatead.notification.model;

import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import dev.lourencogabriel.inatead.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "notification_recipients",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_recipients_channel",
                columnNames = {"notification_id", "recipient_user_id", "channel"}
        ),
        indexes = {
                @Index(
                        name = "idx_notification_recipients_inbox",
                        columnList = "recipient_user_id,channel,read_at"
                ),
                @Index(
                        name = "idx_notification_recipients_delivery",
                        columnList = "delivery_status,created_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationRecipient extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20, nullable = false)
    private NotificationDeliveryStatus deliveryStatus = NotificationDeliveryStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
