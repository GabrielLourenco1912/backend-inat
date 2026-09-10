package dev.lourencogabriel.inatead.cohort.dto;

import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.dto.LifecycleStatusHistoryResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CohortEnrollmentDtos {

    private CohortEnrollmentDtos() {
    }

    public record Request(
            @NotBlank String contractId,
            @NotBlank String cohortId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull EnrollmentStatus status,
            @Size(max = 500) String statusReason
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
            List<LifecycleStatusHistoryResponse> statusHistory,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
