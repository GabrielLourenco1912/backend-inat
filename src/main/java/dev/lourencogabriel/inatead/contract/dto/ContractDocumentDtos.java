package dev.lourencogabriel.inatead.contract.dto;

import dev.lourencogabriel.inatead.document.dto.StoredFileDtos;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ContractDocumentDtos {

    private ContractDocumentDtos() {
    }

    public record Request(
            @NotBlank String contractId,
            @NotNull Short documentTypeId,
            @Min(1) int versionNumber,
            boolean current
    ) {
    }

    public record Response(
            String id,
            String contractId,
            StoredFileDtos.Response file,
            Short documentTypeId,
            int versionNumber,
            boolean current,
            Instant createdAt
    ) {
    }
}
