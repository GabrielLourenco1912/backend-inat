package dev.lourencogabriel.inatead.learner.controller;

import dev.lourencogabriel.inatead.learner.dto.LearnerOnboardingDtos;
import dev.lourencogabriel.inatead.learner.service.LearnerOnboardingService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner-onboardings")
@RequiredArgsConstructor
public class LearnerOnboardingController {

    private final LearnerOnboardingService service;

    @PostMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ResponseEntity<ApiResponse<LearnerOnboardingDtos.Response>> onboard(
            @Valid @RequestBody LearnerOnboardingDtos.Request request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.onboard(request),
                "Learner onboarding completed",
                HttpStatus.CREATED
        ));
    }
}
