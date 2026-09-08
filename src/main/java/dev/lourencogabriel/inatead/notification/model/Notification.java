package dev.lourencogabriel.inatead.notification.model;

import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_schedule",
                        columnList = "scheduled_at,expires_at"
                ),
                @Index(
                        name = "idx_notifications_context",
                        columnList = "context_type,context_id"
                ),
                @Index(
                        name = "idx_notifications_audience",
                        columnList = "audience_type,audience_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Notification extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", length = 20, nullable = false)
    private NotificationAudienceType audienceType;

    @Column(name = "audience_id", length = 26)
    private String audienceId;

    @Column(name = "notification_type", length = 50, nullable = false)
    private String notificationType;

    @Column(length = 160, nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String message;

    @Column(name = "action_url", length = 512)
    private String actionUrl;

    @Column(name = "context_type", length = 50)
    private String contextType;

    @Column(name = "context_id", length = 26)
    private String contextId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
