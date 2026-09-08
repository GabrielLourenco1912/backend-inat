package dev.lourencogabriel.inatead.activity.controller;

import dev.lourencogabriel.inatead.activity.dto.ActivitySubmissionDtos;
import dev.lourencogabriel.inatead.activity.service.ActivitySubmissionService;
import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity-submissions")
@RequiredArgsConstructor
@Validated
public class ActivitySubmissionController {

    private final ActivitySubmissionService service;
    private final DomainAuthorization authorization;

    @PostMapping
    @PreAuthorize("@domainAuthorization.canCreateSubmission(authentication, #request.activityId(), #request.learnerId())")
    public ApiResponse<ActivitySubmissionDtos.Response> create(
            @Valid @RequestBody ActivitySubmissionDtos.SaveRequest request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.create(request, authorization.userId(authentication)),
                "Submission created"
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessSubmission(authentication, #id)")
    public ApiResponse<ActivitySubmissionDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Submission found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<ActivitySubmissionDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Submissions found"
        );
    }

    @GetMapping("/activity/{activityId}")
    @PreAuthorize("@domainAuthorization.canManageActivity(authentication, #activityId)")
    public ApiResponse<List<ActivitySubmissionDtos.Response>> findByActivity(
            @PathVariable String activityId
    ) {
        return new ApiResponse<>(
                service.findByActivity(activityId),
                "Activity submissions found"
        );
    }

    @GetMapping("/learner/{learnerId}")
    @PreAuthorize("@domainAuthorization.canAccessOwnLearnerData(authentication, #learnerId)")
    public ApiResponse<List<ActivitySubmissionDtos.Response>> findByLearner(
            @PathVariable String learnerId
    ) {
        return new ApiResponse<>(service.findByLearner(learnerId), "Learner submissions found");
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canEditSubmission(authentication, #id)")
    public ApiResponse<ActivitySubmissionDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody ActivitySubmissionDtos.SaveRequest request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.update(id, request, authorization.userId(authentication)),
                "Submission updated"
        );
    }

    @PatchMapping("/{id}/grade")
    @PreAuthorize("@domainAuthorization.canGradeSubmission(authentication, #id)")
    public ApiResponse<ActivitySubmissionDtos.Response> grade(
            @PathVariable String id,
            @Valid @RequestBody ActivitySubmissionDtos.GradeRequest request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.grade(id, request, authorization.userId(authentication)),
                "Submission graded"
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canEditSubmission(authentication, #id)")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Submission deleted");
    }
}
