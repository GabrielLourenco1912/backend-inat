package dev.lourencogabriel.inatead.learner.controller;

import dev.lourencogabriel.inatead.learner.dto.LearnerGuardianDtos;
import dev.lourencogabriel.inatead.learner.service.LearnerGuardianService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/learners/{learnerId}/guardians")
@RequiredArgsConstructor
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class LearnerGuardianController {

    private final LearnerGuardianService service;

    @PostMapping
    public ApiResponse<LearnerGuardianDtos.Response> create(
            @PathVariable String learnerId,
            @Valid @RequestBody LearnerGuardianDtos.Request request
    ) {
        return new ApiResponse<>(service.create(learnerId, request), "Guardian linked");
    }

    @GetMapping
    public ApiResponse<List<LearnerGuardianDtos.Response>> findByLearner(
            @PathVariable String learnerId
    ) {
        return new ApiResponse<>(service.findByLearner(learnerId), "Guardians found");
    }

    @PutMapping("/{guardianPersonId}")
    public ApiResponse<LearnerGuardianDtos.Response> update(
            @PathVariable String learnerId,
            @PathVariable String guardianPersonId,
            @Valid @RequestBody LearnerGuardianDtos.Request request
    ) {
        return new ApiResponse<>(
                service.update(learnerId, guardianPersonId, request),
                "Guardian link updated"
        );
    }

    @DeleteMapping("/{guardianPersonId}")
    public ApiResponse<Void> delete(
            @PathVariable String learnerId,
            @PathVariable String guardianPersonId
    ) {
        service.delete(learnerId, guardianPersonId);
        return new ApiResponse<>(null, "Guardian unlinked");
    }
}
