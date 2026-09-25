package dev.lourencogabriel.inatead.learner.dto;

import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class LearnerOnboardingDtos {

    private LearnerOnboardingDtos() {
    }

    public record Request(
            @NotNull @Valid PersonDtos.Request person,
            @NotNull @Valid LearnerProfile learner,
            @NotNull @Size(max = 10)
            List<@NotNull @Valid Guardian> guardians
    ) {
    }

    public record ExistingPersonRequest(
            @NotNull @Valid LearnerProfile learner,
            @NotNull @Size(max = 10)
            List<@NotNull @Valid Guardian> guardians
    ) {
    }

    public record LearnerProfile(
            @NotBlank @Size(max = 30) String registrationNumber,
            boolean hasCompletedHighSchool,
            @NotNull RecordStatus status
    ) {
    }

    public record Guardian(
            @NotBlank String guardianPersonId,
            @NotBlank @Size(max = 30) String relationshipType,
            boolean legalGuardian,
            boolean primaryContact
    ) {
    }

    public record Response(
            PersonDtos.Response person,
            LearnerDtos.Response learner,
            List<LearnerGuardianDtos.Response> guardians,
            boolean personCreated
    ) {
    }
}
