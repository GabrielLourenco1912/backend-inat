package dev.lourencogabriel.inatead.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AddressDtos {

    private AddressDtos() {
    }

    public record Request(
            @NotBlank @Pattern(regexp = "\\d{8}") String postalCode,
            @NotBlank @Size(max = 150) String street,
            @NotBlank @Size(max = 20) String streetNumber,
            @Size(max = 100) String addressLine2,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String stateCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode
    ) {
    }

    public record Response(
            String id,
            String postalCode,
            String street,
            String streetNumber,
            String addressLine2,
            String district,
            String city,
            String stateCode,
            String countryCode,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
