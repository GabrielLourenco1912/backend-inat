package dev.lourencogabriel.inatead.activity.dto;

import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class ActivityDtos {

    private ActivityDtos() {
    }

    public record Request(
            @NotBlank String lessonId,
            @NotBlank @Size(max = 160) String title,
            @NotBlank String description,
            @NotNull Instant availableAt,
            @NotNull Instant dueAt,
            @DecimalMin("0.00") BigDecimal maxScore,
            @NotNull ActivityStatus status
    ) {
    }

    public record Response(
            String id,
            String lessonId,
            String createdByUserId,
            String title,
            String description,
            Instant availableAt,
            Instant dueAt,
            BigDecimal maxScore,
            ActivityStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
