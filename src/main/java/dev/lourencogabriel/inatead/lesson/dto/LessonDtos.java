package dev.lourencogabriel.inatead.lesson.dto;

import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class LessonDtos {

    private LessonDtos() {
    }

    public record Request(
            @NotBlank String cohortId,
            @NotBlank String instructorPersonId,
            @NotBlank @Size(max = 160) String title,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull DeliveryMode deliveryMode,
            @Size(max = 80) String room,
            @Size(max = 512) String meetingUrl,
            @NotNull LessonStatus status
    ) {
    }

    public record Response(
            String id,
            String cohortId,
            String instructorPersonId,
            String title,
            Instant startsAt,
            Instant endsAt,
            DeliveryMode deliveryMode,
            String room,
            String meetingUrl,
            LessonStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
