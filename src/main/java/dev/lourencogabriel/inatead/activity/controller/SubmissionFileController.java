package dev.lourencogabriel.inatead.activity.controller;

import dev.lourencogabriel.inatead.activity.dto.AttachmentDtos;
import dev.lourencogabriel.inatead.activity.service.SubmissionFileService;
import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.document.controller.StoredFileResponseFactory;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
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
@RequestMapping("/api/activity-submissions/{submissionId}/files")
@RequiredArgsConstructor
public class SubmissionFileController {

    private final SubmissionFileService service;
    private final DomainAuthorization authorization;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@domainAuthorization.canEditSubmission(authentication, #submissionId)")
    public ResponseEntity<ApiResponse<AttachmentDtos.SubmissionFileResponse>> attach(
            @PathVariable String submissionId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.attach(submissionId, file, authorization.userId(authentication)),
                "File uploaded and attached",
                HttpStatus.CREATED
        ));
    }

    @GetMapping("/{fileId}/content")
    @PreAuthorize("@domainAuthorization.canAccessSubmission(authentication, #submissionId)")
    public ResponseEntity<Resource> download(
            @PathVariable String submissionId,
            @PathVariable String fileId
    ) {
        return StoredFileResponseFactory.attachment(service.download(submissionId, fileId));
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.canAccessSubmission(authentication, #submissionId)")
    public ApiResponse<List<AttachmentDtos.SubmissionFileResponse>> findAll(
            @PathVariable String submissionId
    ) {
        return new ApiResponse<>(service.findBySubmission(submissionId), "Submission files found");
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("@domainAuthorization.canEditSubmission(authentication, #submissionId)")
    public ApiResponse<Void> detach(
            @PathVariable String submissionId,
            @PathVariable String fileId
    ) {
        service.detach(submissionId, fileId);
        return new ApiResponse<>(null, "File and attachment deleted");
    }
}
