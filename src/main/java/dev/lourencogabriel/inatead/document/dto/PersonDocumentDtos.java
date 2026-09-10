package dev.lourencogabriel.inatead.document.dto;

import dev.lourencogabriel.inatead.document.model.PersonDocumentStatusChangeReason;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PersonDocumentDtos {

    private PersonDocumentDtos() {
    }

    public record Request(
            @NotBlank String personId,
            @NotNull Short documentTypeId,
            @Size(max = 80) String documentNumber,
            LocalDate issuedOn,
            LocalDate expiresOn
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
            List<StatusHistoryResponse> statusHistory,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record StatusHistoryResponse(
            String id,
            DocumentVerificationStatus previousStatus,
            DocumentVerificationStatus newStatus,
            PersonDocumentStatusChangeReason changeReason,
            String changedByUserId,
            Instant changedAt
    ) {
    }
}
