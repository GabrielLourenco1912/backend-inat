package dev.lourencogabriel.inatead.organization.dto;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record Request(
            String parentOrganizationId,
            @NotNull @Valid AddressDtos.Request address,
            @NotEmpty Set<@NotNull OrganizationType> organizationTypes,
            @NotBlank @Size(max = 180) String legalName,
            @Size(max = 180) String tradeName,
            @NotBlank @Pattern(
                    regexp = "[A-Za-z0-9]{12}[0-9]{2}",
                    message = "Informe CNPJ sem formatação: 12 letras ou números e 2 dígitos finais")
            String taxId,
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
            String parentOrganizationName,
            AddressDtos.Response address,
            Set<OrganizationType> organizationTypes,
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
