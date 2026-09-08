package dev.lourencogabriel.inatead.organization.dto;

import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class OrganizationMembershipDtos {

    private OrganizationMembershipDtos() {
    }

    public record Request(
            @NotBlank String personId,
            @NotBlank String organizationId,
            @NotBlank @Size(max = 40) String membershipRole,
            @Size(max = 100) String jobTitle,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull RecordStatus status
    ) {
    }

    public record Response(
            String id,
            String personId,
            String organizationId,
            String membershipRole,
            String jobTitle,
            LocalDate startDate,
            LocalDate endDate,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
