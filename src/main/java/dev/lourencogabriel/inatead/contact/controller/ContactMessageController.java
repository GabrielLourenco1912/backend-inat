package dev.lourencogabriel.inatead.contact.controller;

import dev.lourencogabriel.inatead.contact.dto.ContactMessageDtos;
import dev.lourencogabriel.inatead.contact.model.ContactMessageStatus;
import dev.lourencogabriel.inatead.contact.model.ContactMessageType;
import dev.lourencogabriel.inatead.contact.service.ContactMessageService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact-messages")
@RequiredArgsConstructor
@Validated
public class ContactMessageController {

    private static final String ADMIN_ONLY =
            "@domainAuthorization.hasAnyRole(authentication, 'ADMIN')";

    private final ContactMessageService service;

    @PostMapping
    public ResponseEntity<ApiResponse<ContactMessageDtos.Response>> create(
            @Valid @RequestBody ContactMessageDtos.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.create(request),
                "Contact message created",
                HttpStatus.CREATED
        ));
    }

    @GetMapping
    @PreAuthorize(ADMIN_ONLY)
    public ApiResponse<PageResponse<ContactMessageDtos.Response>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContactMessageStatus status,
            @RequestParam(required = false) ContactMessageType contactType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return new ApiResponse<>(
                service.findAll(search, status, contactType, pageable),
                "Contact messages found"
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    public ApiResponse<ContactMessageDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Contact message found");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(ADMIN_ONLY)
    public ApiResponse<ContactMessageDtos.Response> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody ContactMessageDtos.StatusRequest request
    ) {
        return new ApiResponse<>(service.updateStatus(id, request), "Contact message updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN_ONLY)
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Contact message deleted");
    }
}
