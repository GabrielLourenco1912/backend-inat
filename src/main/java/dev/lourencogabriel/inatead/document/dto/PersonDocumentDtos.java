package dev.lourencogabriel.inatead.document.dto;

import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class PersonDocumentDtos {

    private PersonDocumentDtos() {
    }

    public record Request(
            @NotBlank String personId,
            @NotNull Short documentTypeId,
            @Size(max = 80) String documentNumber,
            LocalDate issuedOn,
            LocalDate expiresOn,
            @NotNull DocumentVerificationStatus verificationStatus
    ) {
    }

    public record Response(
            String id,
            String personId,
            StoredFileDtos.Response file,
            Short documentTypeId,
            String documentNumber,
            LocalDate issuedOn,
            LocalDate expiresOn,
            DocumentVerificationStatus verificationStatus,
            String verifiedByUserId,
            Instant verifiedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
