package dev.lourencogabriel.inatead.activity.controller;

import dev.lourencogabriel.inatead.activity.dto.ActivityDtos;
import dev.lourencogabriel.inatead.activity.service.ActivityService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Validated
public class ActivityController {

    private final ActivityService service;
    private final DomainAuthorization authorization;

    @PostMapping
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #request.lessonId())")
    public ApiResponse<ActivityDtos.Response> create(
            @Valid @RequestBody ActivityDtos.Request request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.create(request, authorization.userId(authentication)),
                "Activity created"
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessActivity(authentication, #id)")
    public ApiResponse<ActivityDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Activity found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<ActivityDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Activities found"
        );
    }

    @GetMapping("/lesson/{lessonId}")
    @PreAuthorize("@domainAuthorization.canAccessLesson(authentication, #lessonId)")
    public ApiResponse<List<ActivityDtos.Response>> findByLesson(
            @PathVariable String lessonId,
            Authentication authentication
    ) {
        boolean includeDrafts = authorization.canManageLesson(authentication, lessonId);
        return new ApiResponse<>(
                service.findByLesson(lessonId, includeDrafts),
                "Lesson activities found"
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageActivity(authentication, #id)")
    public ApiResponse<ActivityDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody ActivityDtos.Request request
    ) {
        return new ApiResponse<>(service.update(id, request), "Activity updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageActivity(authentication, #id)")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Activity deleted");
    }
}
