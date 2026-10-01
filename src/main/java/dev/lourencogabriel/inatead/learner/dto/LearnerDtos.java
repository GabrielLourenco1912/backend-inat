package dev.lourencogabriel.inatead.learner.dto;

import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class LearnerDtos {

    private LearnerDtos() {
    }

    public record Request(
            @NotBlank String personId,
            boolean hasCompletedHighSchool,
            @NotNull RecordStatus status
    ) {
    }

    public record Response(
            String id,
            String personId,
            String fullName,
            Long registrationNumber,
            boolean hasCompletedHighSchool,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
