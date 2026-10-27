package dev.lourencogabriel.inatead.lesson.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.lesson.dto.LessonParticipantDtos;
import dev.lourencogabriel.inatead.lesson.service.LessonParticipantService;
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
@RequestMapping("/api/lesson-participants")
@RequiredArgsConstructor
@Validated
public class LessonParticipantController {

    private final LessonParticipantService service;
    private final DomainAuthorization authorization;

    @PostMapping
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #request.lessonId())")
    public ApiResponse<LessonParticipantDtos.Response> create(
            @Valid @RequestBody LessonParticipantDtos.Request request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.create(request, authorization.userId(authentication)),
                "Lesson participant created"
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessParticipant(authentication, #id)")
    public ApiResponse<LessonParticipantDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Lesson participant found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<LessonParticipantDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Lesson participants found"
        );
    }

    @GetMapping("/lesson/{lessonId}")
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #lessonId)")
    public ApiResponse<List<LessonParticipantDtos.Response>> findByLesson(
            @PathVariable String lessonId
    ) {
        return new ApiResponse<>(service.findByLesson(lessonId), "Lesson roster found");
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageParticipant(authentication, #id)")
    public ApiResponse<LessonParticipantDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody LessonParticipantDtos.Request request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.update(id, request, authorization.userId(authentication)),
                "Lesson participant updated"
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageParticipant(authentication, #id)")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Lesson participant deleted");
    }

    @PostMapping("/lesson/{lessonId}/generate-roster")
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #lessonId)")
    public ApiResponse<LessonParticipantDtos.RosterGenerationResponse> generateRoster(
            @PathVariable String lessonId
    ) {
        return new ApiResponse<>(
                service.generateRegularRoster(lessonId),
                "Regular lesson roster generated"
        );
    }

    @PostMapping(value="/lesson/{lessonId}/generate-roster",params="view=summary")
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #lessonId)")
    public ApiResponse<java.util.Map<String,Object>> generateRosterSummary(@PathVariable String lessonId) {
        var result=service.generateRegularRoster(lessonId,false);
        return new ApiResponse<>(java.util.Map.of("lessonId",result.lessonId(),"createdCount",result.createdCount(),"existingCount",result.existingCount()),"Regular lesson roster generated");
    }

    @PostMapping("/reassign")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<LessonParticipantDtos.ReassignmentResponse> reassign(
            @Valid @RequestBody LessonParticipantDtos.ReassignRequest request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.reassign(request, authorization.userId(authentication)),
                "Learner reassigned"
        );
    }
}
