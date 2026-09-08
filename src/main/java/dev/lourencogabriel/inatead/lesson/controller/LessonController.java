package dev.lourencogabriel.inatead.lesson.controller;

import dev.lourencogabriel.inatead.lesson.dto.LessonDtos;
import dev.lourencogabriel.inatead.lesson.service.LessonService;
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
import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;

@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
@Validated
public class LessonController {

    private final LessonService service;
    private final DomainAuthorization authorization;

    @PostMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<LessonDtos.Response> create(@Valid @RequestBody LessonDtos.Request request) {
        return new ApiResponse<>(service.create(request), "Lesson created");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessLesson(authentication, #id)")
    public ApiResponse<LessonDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Lesson found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<LessonDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Lessons found"
        );
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<LessonDtos.Response>> findMine(Authentication authentication) {
        return new ApiResponse<>(
                service.findMine(authorization.userId(authentication)),
                "Accessible lessons found"
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #id)")
    public ApiResponse<LessonDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody LessonDtos.Request request
    ) {
        return new ApiResponse<>(service.update(id, request), "Lesson updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #id)")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Lesson deleted");
    }
}
