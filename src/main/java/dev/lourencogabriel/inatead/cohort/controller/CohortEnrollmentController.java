package dev.lourencogabriel.inatead.cohort.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.cohort.dto.CohortEnrollmentDtos;
import dev.lourencogabriel.inatead.cohort.service.CohortEnrollmentService;
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

@RestController
@RequestMapping("/api/cohort-enrollments")
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
@RequiredArgsConstructor
@Validated
public class CohortEnrollmentController {

    private final CohortEnrollmentService service;
    private final DomainAuthorization domainAuthorization;

    @PostMapping
    public ResponseEntity<ApiResponse<CohortEnrollmentDtos.Response>> create(
            @Valid @RequestBody CohortEnrollmentDtos.Request request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.create(request, domainAuthorization.userId(authentication)),
                "Cohort enrollment created",
                HttpStatus.CREATED
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<CohortEnrollmentDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Cohort enrollment found");
    }

    @GetMapping
    public ApiResponse<PageResponse<CohortEnrollmentDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Cohort enrollments found"
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<CohortEnrollmentDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody CohortEnrollmentDtos.Request request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.update(id, request, domainAuthorization.userId(authentication)),
                "Cohort enrollment updated"
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Cohort enrollment deleted");
    }
}
