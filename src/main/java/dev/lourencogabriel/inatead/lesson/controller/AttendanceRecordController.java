package dev.lourencogabriel.inatead.lesson.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.lesson.dto.AttendanceRecordDtos;
import dev.lourencogabriel.inatead.lesson.service.AttendanceRecordService;
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
@RequestMapping("/api/attendance-records")
@RequiredArgsConstructor
@Validated
public class AttendanceRecordController {

    private final AttendanceRecordService service;
    private final DomainAuthorization authorization;

    @PostMapping
    @PreAuthorize("@domainAuthorization.canManageParticipant(authentication, #request.lessonParticipantId())")
    public ApiResponse<AttendanceRecordDtos.Response> create(
            @Valid @RequestBody AttendanceRecordDtos.Request request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.create(request, authorization.userId(authentication)),
                "Attendance recorded"
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessAttendance(authentication, #id)")
    public ApiResponse<AttendanceRecordDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Attendance record found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<AttendanceRecordDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Attendance records found"
        );
    }

    @GetMapping("/lesson/{lessonId}")
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #lessonId)")
    public ApiResponse<List<AttendanceRecordDtos.Response>> findByLesson(
            @PathVariable String lessonId
    ) {
        return new ApiResponse<>(
                service.findByLesson(lessonId),
                "Lesson attendance records found"
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageAttendance(authentication, #id)")
    public ApiResponse<AttendanceRecordDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody AttendanceRecordDtos.Request request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.update(id, request, authorization.userId(authentication)),
                "Attendance record updated"
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canManageAttendance(authentication, #id)")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Attendance record deleted");
    }
}
