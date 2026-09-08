package dev.lourencogabriel.inatead.document.controller;

import dev.lourencogabriel.inatead.document.dto.DocumentTypeDtos;
import dev.lourencogabriel.inatead.document.service.DocumentTypeService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
@RequestMapping("/api/document-types")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class DocumentTypeController {

    private final DocumentTypeService service;

    @PostMapping
    public ApiResponse<DocumentTypeDtos.Response> create(@Valid @RequestBody DocumentTypeDtos.Request request) {
        return new ApiResponse<>(service.create(request), "Document type created");
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentTypeDtos.Response> findById(@PathVariable Short id) {
        return new ApiResponse<>(service.findById(id), "Document type found");
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentTypeDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Document types found"
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<DocumentTypeDtos.Response> update(
            @PathVariable Short id,
            @Valid @RequestBody DocumentTypeDtos.Request request
    ) {
        return new ApiResponse<>(service.update(id, request), "Document type updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Short id) {
        service.delete(id);
        return new ApiResponse<>(null, "Document type deleted");
    }
}
