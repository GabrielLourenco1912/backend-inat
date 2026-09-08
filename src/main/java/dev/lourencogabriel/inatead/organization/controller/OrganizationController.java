package dev.lourencogabriel.inatead.organization.controller;

import dev.lourencogabriel.inatead.organization.dto.OrganizationDtos;
import dev.lourencogabriel.inatead.organization.service.OrganizationService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Validated
public class OrganizationController {

    private final OrganizationService service;

    @PostMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationDtos.Response>> create(
            @Valid @RequestBody OrganizationDtos.Request request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.create(request),
                "Organization created",
                HttpStatus.CREATED
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessOrganization(authentication, #id)")
    public ApiResponse<OrganizationDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Organization found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<OrganizationDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Organizations found"
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<OrganizationDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody OrganizationDtos.Request request
    ) {
        return new ApiResponse<>(service.update(id, request), "Organization updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Organization deleted");
    }
}
