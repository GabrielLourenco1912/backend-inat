package dev.lourencogabriel.inatead.notification.dto;

import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(max = 50)
            @Pattern(regexp = "[A-Z][A-Z0-9_]*")
            String notificationType,
            @NotBlank @Size(max = 160) String title,
            @NotBlank String message,
            @Size(max = 512) String actionUrl,
            @Size(max = 50) String contextType,
            @Size(max = 26) String contextId,
            String payload,
            @NotNull NotificationPriority priority,
            Instant scheduledAt,
            Instant expiresAt,
            @NotNull @Valid AudienceRequest audience,
            @NotEmpty Set<@NotNull NotificationChannel> channels
    ) {
    }

    public record UpdateRequest(
            @NotBlank
            @Size(max = 50)
            @Pattern(regexp = "[A-Z][A-Z0-9_]*")
            String notificationType,
            @NotBlank @Size(max = 160) String title,
            @NotBlank String message,
            @Size(max = 512) String actionUrl,
            @Size(max = 50) String contextType,
            @Size(max = 26) String contextId,
            String payload,
            @NotNull NotificationPriority priority,
            Instant scheduledAt,
            Instant expiresAt
    ) {
    }

    public record AudienceRequest(
            @NotNull NotificationAudienceType type,
            @Size(max = 26) String targetId
    ) {
    }

    public record AudienceResponse(
            NotificationAudienceType type,
            String targetId
    ) {
    }

    public record Response(
            String id,
            String createdByUserId,
            AudienceResponse audience,
            String notificationType,
            String title,
            String message,
            String actionUrl,
            String contextType,
            String contextId,
            String payload,
            NotificationPriority priority,
            Instant scheduledAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
