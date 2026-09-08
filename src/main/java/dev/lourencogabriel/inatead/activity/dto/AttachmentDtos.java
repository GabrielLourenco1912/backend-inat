package dev.lourencogabriel.inatead.activity.dto;

import dev.lourencogabriel.inatead.document.dto.StoredFileDtos;
import jakarta.validation.constraints.Min;

import java.time.Instant;

public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record ActivityFileRequest(@Min(0) int sortOrder) {
    }

    public record ActivityFileResponse(
            String activityId,
            StoredFileDtos.Response file,
            int sortOrder,
            Instant createdAt
    ) {
    }

    public record SubmissionFileResponse(
            String submissionId,
            StoredFileDtos.Response file,
            Instant createdAt
    ) {
    }
}
