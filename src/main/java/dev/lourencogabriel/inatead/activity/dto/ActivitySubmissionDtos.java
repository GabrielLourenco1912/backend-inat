package dev.lourencogabriel.inatead.activity.dto;

import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class ActivitySubmissionDtos {

    private ActivitySubmissionDtos() {
    }

    public record SaveRequest(
            @NotBlank String activityId,
            @NotBlank String learnerId,
            String textAnswer,
            @NotNull SubmissionStatus status
    ) {
    }

    public record GradeRequest(
            @DecimalMin("0.00") BigDecimal score,
            String feedback,
            @NotNull SubmissionStatus status
    ) {
    }

    public record Response(
            String id,
            String activityId,
            String learnerId,
            String gradedByUserId,
            String textAnswer,
            SubmissionStatus status,
            Instant submittedAt,
            BigDecimal score,
            String feedback,
            Instant gradedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
