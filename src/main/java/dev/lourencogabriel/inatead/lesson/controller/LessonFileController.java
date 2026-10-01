package dev.lourencogabriel.inatead.lesson.controller;

import dev.lourencogabriel.inatead.lesson.dto.LessonFileDtos;
import dev.lourencogabriel.inatead.lesson.service.LessonFileService;
import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.document.controller.StoredFileResponseFactory;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/lessons/{lessonId}/files")
@RequiredArgsConstructor
public class LessonFileController {

    private final LessonFileService service;
    private final DomainAuthorization authorization;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #lessonId)")
    public ResponseEntity<ApiResponse<LessonFileDtos.Response>> attach(
            @PathVariable String lessonId,
            @Valid @RequestPart("metadata") LessonFileDtos.Request request,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.attach(
                        lessonId,
                        request,
                        file,
                        authorization.userId(authentication)
                ),
                "File uploaded and attached",
                HttpStatus.CREATED
        ));
    }

    @GetMapping("/{fileId}/content")
    @PreAuthorize("@domainAuthorization.canAccessLesson(authentication, #lessonId)")
    public ResponseEntity<Resource> download(
            @PathVariable String lessonId,
            @PathVariable String fileId
    ) {
        return StoredFileResponseFactory.attachment(service.download(lessonId, fileId));
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.canAccessLesson(authentication, #lessonId)")
    public ApiResponse<List<LessonFileDtos.Response>> findAll(
            @PathVariable String lessonId
    ) {
        return new ApiResponse<>(service.findByLesson(lessonId), "Lesson files found");
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("@domainAuthorization.canManageLesson(authentication, #lessonId)")
    public ApiResponse<Void> detach(
            @PathVariable String lessonId,
            @PathVariable String fileId
    ) {
        service.detach(lessonId, fileId);
        return new ApiResponse<>(null, "File and attachment deleted");
    }
}
