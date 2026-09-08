package dev.lourencogabriel.inatead.lesson.dto;

import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AttendanceRecordDtos {

    private AttendanceRecordDtos() {
    }

    public record Request(
            @NotBlank String lessonParticipantId,
            @NotNull AttendanceStatus status,
            Instant checkInAt,
            Instant checkOutAt,
            @Size(max = 500) String notes
    ) {
    }

    public record Response(
            String id,
            String lessonParticipantId,
            String lessonId,
            String learnerId,
            String recordedByUserId,
            AttendanceStatus status,
            Instant checkInAt,
            Instant checkOutAt,
            Instant recordedAt,
            String notes,
            Instant updatedAt
    ) {
    }
}
