package dev.lourencogabriel.inatead.lesson.controller;
import dev.lourencogabriel.inatead.lesson.service.PortalAttendanceService;
import dev.lourencogabriel.inatead.search.PortalReadService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lessons/{lessonId}/attendance")
@PreAuthorize("@domainAuthorization.canManageLesson(authentication, #lessonId)")
public class PortalAttendanceController {
    private final PortalAttendanceService attendance;
    private final PortalReadService reads;
    @GetMapping("/missing-count")
    public ApiResponse<Map<String,Long>> count(@PathVariable String lessonId,@RequestParam Map<String,String> filters,Authentication authentication) { return new ApiResponse<>(Map.of("count",reads.count("lesson-attendance",PortalReadService.attendanceFilters(lessonId,filters),authentication)),"Missing attendance count"); }
    @PostMapping("/batch")
    public ApiResponse<List<PortalAttendanceService.Outcome>> save(@PathVariable String lessonId,@RequestBody @Valid PortalAttendanceService.Batch batch,Authentication authentication) { return new ApiResponse<>(attendance.save(lessonId,batch.items(),authentication),"Attendance results"); }
    @PostMapping("/fill-missing")
    public ApiResponse<Map<String,Object>> fill(@PathVariable String lessonId,@RequestBody Map<String,String> filters,Authentication authentication) { return new ApiResponse<>(attendance.fill(lessonId,filters,authentication),"Missing attendance results"); }
}
