package dev.lourencogabriel.inatead.learner.controller;

import dev.lourencogabriel.inatead.learner.dto.LearnerOnboardingDtos;
import dev.lourencogabriel.inatead.learner.service.LearnerOnboardingService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LearnerFromPersonController {

    private final LearnerOnboardingService service;

    @PostMapping("/api/people/{personId}/learner")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ResponseEntity<ApiResponse<LearnerOnboardingDtos.Response>> create(
            @PathVariable String personId,
            @Valid @RequestBody LearnerOnboardingDtos.ExistingPersonRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.fromPerson(personId, request),
                "Learner profile created",
                HttpStatus.CREATED
        ));
    }
}
