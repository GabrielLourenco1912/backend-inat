package dev.lourencogabriel.inatead.contract.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.contract.dto.ContractDocumentDtos;
import dev.lourencogabriel.inatead.contract.service.ContractDocumentService;
import dev.lourencogabriel.inatead.document.controller.StoredFileResponseFactory;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/contract-documents")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class ContractDocumentController {

    private final ContractDocumentService service;
    private final DomainAuthorization authorization;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ContractDocumentDtos.Response>> create(
            @Valid @RequestPart("metadata") ContractDocumentDtos.Request request,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.create(request, file, authorization.userId(authentication)),
                "Contract document created",
                HttpStatus.CREATED
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractDocumentDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Contract document found");
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        return StoredFileResponseFactory.attachment(service.download(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<ContractDocumentDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String contractId
    ) {
        return new ApiResponse<>(
                service.findAll(contractId, PageRequest.of(
                        page, size, Sort.by("createdAt").descending().and(Sort.by("id"))
                )),
                "Contract documents found"
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<ContractDocumentDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody ContractDocumentDtos.Request request
    ) {
        return new ApiResponse<>(service.update(id, request), "Contract document updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Contract document deleted");
    }
}
