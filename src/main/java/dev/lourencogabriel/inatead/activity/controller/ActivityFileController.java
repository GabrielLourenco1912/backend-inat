package dev.lourencogabriel.inatead.activity.controller;

import dev.lourencogabriel.inatead.activity.dto.AttachmentDtos;
import dev.lourencogabriel.inatead.activity.service.ActivityFileService;
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
@RequestMapping("/api/activities/{activityId}/files")
@RequiredArgsConstructor
public class ActivityFileController {

    private final ActivityFileService service;
    private final DomainAuthorization authorization;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@domainAuthorization.canManageActivity(authentication, #activityId)")
    public ResponseEntity<ApiResponse<AttachmentDtos.ActivityFileResponse>> attach(
            @PathVariable String activityId,
            @Valid @RequestPart("metadata") AttachmentDtos.ActivityFileRequest request,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.attach(
                        activityId,
                        request,
                        file,
                        authorization.userId(authentication)
                ),
                "File uploaded and attached",
                HttpStatus.CREATED
        ));
    }

    @GetMapping("/{fileId}/content")
    @PreAuthorize("@domainAuthorization.canAccessActivity(authentication, #activityId)")
    public ResponseEntity<Resource> download(
            @PathVariable String activityId,
            @PathVariable String fileId
    ) {
        return StoredFileResponseFactory.attachment(service.download(activityId, fileId));
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.canAccessActivity(authentication, #activityId)")
    public ApiResponse<List<AttachmentDtos.ActivityFileResponse>> findAll(
            @PathVariable String activityId
    ) {
        return new ApiResponse<>(service.findByActivity(activityId), "Activity files found");
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("@domainAuthorization.canManageActivity(authentication, #activityId)")
    public ApiResponse<Void> detach(
            @PathVariable String activityId,
            @PathVariable String fileId
    ) {
        service.detach(activityId, fileId);
        return new ApiResponse<>(null, "File and attachment deleted");
    }
}
