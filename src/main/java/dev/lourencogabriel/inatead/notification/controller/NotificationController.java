package dev.lourencogabriel.inatead.notification.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.service.NotificationService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class NotificationController {

    private final NotificationService service;
    private final DomainAuthorization authorization;

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationDtos.Response>> create(
            @Valid @RequestBody NotificationDtos.CreateRequest request,
            Authentication authentication
    ) {
        NotificationDtos.Response response = service.create(
                request,
                authorization.userId(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(response, "Notification created", HttpStatus.CREATED));
    }

    @GetMapping("/{id}")
    public ApiResponse<NotificationDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Notification found");
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Notifications found"
        );
    }

    @GetMapping("/context/{contextType}/{contextId}")
    public ApiResponse<List<NotificationDtos.Response>> findByContext(
            @PathVariable @NotBlank String contextType,
            @PathVariable @NotBlank String contextId
    ) {
        return new ApiResponse<>(
                service.findByContext(contextType, contextId),
                "Context notifications found"
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<NotificationDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody NotificationDtos.UpdateRequest request
    ) {
        return new ApiResponse<>(service.update(id, request), "Notification updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Notification deleted");
    }
}
