package dev.lourencogabriel.inatead.organization.dto;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record Request(
            String parentOrganizationId,
            @NotNull @Valid AddressDtos.Request address,
            @NotNull OrganizationType organizationType,
            @NotBlank @Size(max = 180) String legalName,
            @Size(max = 180) String tradeName,
            @NotBlank @Pattern(regexp = "\\d{14}") String taxId,
            @NotBlank @Email @Size(max = 254) String contactEmail,
            @NotBlank @Pattern(
                    regexp = "[1-9]{2}(?:9\\d{8}|[2-5]\\d{7})",
                    message = "Informe DDD e telefone sem formatação")
            String phoneNumber,
            @Min(1) @Max(31) Integer attendanceClosingDay,
            @NotNull RecordStatus status
    ) {
    }

    public record Response(
            String id,
            String parentOrganizationId,
            AddressDtos.Response address,
            OrganizationType organizationType,
            String legalName,
            String tradeName,
            String taxId,
            String contactEmail,
            String phoneNumber,
            Integer attendanceClosingDay,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
