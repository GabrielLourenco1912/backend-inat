package dev.lourencogabriel.inatead.document.dto;

import java.time.Instant;

public final class StoredFileDtos {

    private StoredFileDtos() {
    }

    public record Response(
            String id,
            String uploadedByUserId,
            String originalName,
            String mimeType,
            long sizeBytes,
            String checksumSha256,
            Instant createdAt
    ) {
    }
}
