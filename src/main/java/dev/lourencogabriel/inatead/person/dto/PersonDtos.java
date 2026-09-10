package dev.lourencogabriel.inatead.person.dto;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class PersonDtos {

    private PersonDtos() {
    }

    public record Request(
            @NotNull @Valid AddressDtos.Request address,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = "\\d{11}") String taxId,
            @Email @Size(max = 254) String contactEmail,
            @NotBlank @Size(max = 20) String phoneNumber,
            @NotNull @Past LocalDate birthDate,
            @Size(max = 30) String gender,
            @NotNull RecordStatus status
    ) {
    }

    public record Response(
            String id,
            AddressDtos.Response address,
            String fullName,
            String taxId,
            String contactEmail,
            String phoneNumber,
            LocalDate birthDate,
            String gender,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
