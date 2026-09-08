package dev.lourencogabriel.inatead.notification.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.notification.dto.NotificationRecipientDtos;
import dev.lourencogabriel.inatead.notification.service.NotificationRecipientService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-recipients")
@RequiredArgsConstructor
@Validated
public class NotificationRecipientController {

    private final NotificationRecipientService service;
    private final DomainAuthorization authorization;

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessNotificationRecipient(authentication, #id)")
    public ApiResponse<NotificationRecipientDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Notification recipient found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<NotificationRecipientDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Notification recipients found"
        );
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<NotificationRecipientDtos.Response>> inbox(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.findInbox(
                        authorization.userId(authentication),
                        channel,
                        PageRequest.of(page, size)
                ),
                "Inbox found"
        );
    }

    @GetMapping("/me/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NotificationRecipientDtos.UnreadCount> unreadCount(
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.unreadCount(authorization.userId(authentication)),
                "Unread notification count found"
        );
    }

    @PatchMapping("/{id}/delivery")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<NotificationRecipientDtos.Response> updateDelivery(
            @PathVariable String id,
            @Valid @RequestBody NotificationRecipientDtos.DeliveryRequest request
    ) {
        return new ApiResponse<>(
                service.updateDelivery(id, request),
                "Notification delivery updated"
        );
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("@domainAuthorization.canAccessNotificationRecipient(authentication, #id)")
    public ApiResponse<NotificationRecipientDtos.Response> markRead(
            @PathVariable String id,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.markRead(id, authorization.userId(authentication)),
                "Notification marked as read"
        );
    }

}
