package dev.lourencogabriel.inatead.cohort.dto;

import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class CohortDtos {

    private CohortDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Min(1) @Max(7) int defaultWeekday,
            @NotBlank @Size(max = 20) String shiftCode,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull CohortStatus status
    ) {
    }

    public record Response(
            String id,
            String code,
            String name,
            int defaultWeekday,
            String shiftCode,
            LocalDate startDate,
            LocalDate endDate,
            CohortStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
