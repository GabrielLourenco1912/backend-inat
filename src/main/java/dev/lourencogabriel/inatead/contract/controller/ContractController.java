package dev.lourencogabriel.inatead.contract.controller;

import dev.lourencogabriel.inatead.contract.dto.ContractDtos;
import dev.lourencogabriel.inatead.contract.service.ContractService;
import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
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

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Validated
public class ContractController {

    private final ContractService service;
    private final DomainAuthorization domainAuthorization;

    @PostMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ResponseEntity<ApiResponse<ContractDtos.Response>> create(
            @Valid @RequestBody ContractDtos.Request request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.create(request, domainAuthorization.userId(authentication)),
                "Contract created",
                HttpStatus.CREATED
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.canAccessContract(authentication, #id)")
    public ApiResponse<ContractDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Contract found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<ContractDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Contracts found"
        );
    }

    @GetMapping("/learner/{learnerId}")
    @PreAuthorize("@domainAuthorization.canAccessOwnLearnerData(authentication, #learnerId)")
    public ApiResponse<List<ContractDtos.Response>> findByLearner(
            @PathVariable String learnerId
    ) {
        return new ApiResponse<>(service.findByLearner(learnerId), "Learner contracts found");
    }

    @GetMapping("/organization/{organizationId}")
    @PreAuthorize("@domainAuthorization.canAccessOrganization(authentication, #organizationId)")
    public ApiResponse<List<ContractDtos.Response>> findByOrganization(
            @PathVariable String organizationId
    ) {
        return new ApiResponse<>(
                service.findByOrganization(organizationId),
                "Organization contracts found"
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<ContractDtos.Response> update(
            @PathVariable String id,
            @Valid @RequestBody ContractDtos.Request request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.update(id, request, domainAuthorization.userId(authentication)),
                "Contract updated"
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Contract deleted");
    }
}
