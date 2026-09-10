package dev.lourencogabriel.inatead.contract.dto;

import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.dto.LifecycleStatusHistoryResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ContractDtos {

    private ContractDtos() {
    }

    public record Request(
            @NotBlank String learnerId,
            @NotBlank String employerId,
            String schoolId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal monthlySalary,
            @Min(1) @Max(10080) int weeklyWorkloadMinutes,
            @NotNull ContractStatus status,
            @Size(max = 500) String statusReason
    ) {
    }

    public record Response(
            String id,
            String learnerId,
            String employerId,
            String schoolId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlySalary,
            int weeklyWorkloadMinutes,
            ContractStatus status,
            List<LifecycleStatusHistoryResponse> statusHistory,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
