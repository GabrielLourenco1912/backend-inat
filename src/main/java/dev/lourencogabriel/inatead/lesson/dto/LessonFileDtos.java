package dev.lourencogabriel.inatead.lesson.dto;

import dev.lourencogabriel.inatead.document.dto.StoredFileDtos;
import jakarta.validation.constraints.Min;

import java.time.Instant;

public final class LessonFileDtos {

    private LessonFileDtos() {
    }

    public record Request(@Min(0) int sortOrder) {
    }

    public record Response(
            String lessonId,
            StoredFileDtos.Response file,
            int sortOrder,
            Instant createdAt
    ) {
    }
}
