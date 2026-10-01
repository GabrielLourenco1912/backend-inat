package dev.lourencogabriel.inatead.lesson.dto;

import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LessonParticipantDtos {

    private LessonParticipantDtos() {
    }

    public record Request(
            @NotBlank String lessonId,
            @NotBlank String learnerId,
            String sourceEnrollmentId,
            @NotNull ParticipationType participationType,
            @NotNull ParticipationStatus status,
            @Size(max = 500) String assignmentReason
    ) {
    }

    public record ReassignRequest(
            @NotBlank String sourceParticipantId,
            @NotBlank String targetLessonId,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record Response(
            String id,
            String lessonId,
            String learnerId,
            String learnerName,
            Long learnerRegistrationNumber,
            String sourceEnrollmentId,
            String assignedByUserId,
            ParticipationType participationType,
            ParticipationStatus status,
            String assignmentReason,
            Instant assignedAt,
            Instant updatedAt
    ) {
    }

    public record RosterGenerationResponse(
            String lessonId,
            int createdCount,
            int existingCount,
            List<Response> participants
    ) {
    }

    public record ReassignmentResponse(Response source, Response target) {
    }
}
