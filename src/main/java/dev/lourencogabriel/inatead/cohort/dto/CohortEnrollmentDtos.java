package dev.lourencogabriel.inatead.cohort.dto;

import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public final class CohortEnrollmentDtos {

    private CohortEnrollmentDtos() {
    }

    public record Request(
            @NotBlank String contractId,
            @NotBlank String cohortId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull EnrollmentStatus status
    ) {
    }

    public record Response(
            String id,
            String contractId,
            String cohortId,
            String learnerId,
            LocalDate startDate,
            LocalDate endDate,
            EnrollmentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
