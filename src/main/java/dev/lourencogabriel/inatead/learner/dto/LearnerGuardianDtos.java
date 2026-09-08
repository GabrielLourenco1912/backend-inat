package dev.lourencogabriel.inatead.learner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class LearnerGuardianDtos {

    private LearnerGuardianDtos() {
    }

    public record Request(
            @NotBlank String guardianPersonId,
            @NotBlank @Size(max = 30) String relationshipType,
            boolean legalGuardian,
            boolean primaryContact
    ) {
    }

    public record Response(
            String learnerId,
            String guardianPersonId,
            String guardianName,
            String relationshipType,
            boolean legalGuardian,
            boolean primaryContact,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
