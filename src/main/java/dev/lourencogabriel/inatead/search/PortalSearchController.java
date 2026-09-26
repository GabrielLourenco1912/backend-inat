package dev.lourencogabriel.inatead.search;

import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class PortalSearchController {
    private final PortalSearchService service;

    @GetMapping("/api/search/{resource}")
    public ApiResponse<PageResponse<Object>> search(
            @PathVariable String resource,
            @RequestParam(defaultValue = "") @Size(max = 160) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) @Size(max = 32) String status,
            Authentication authentication) {
        return new ApiResponse<>(service.search(resource, q, page, size, personType, status, "", null, false, authentication), "Search results");
    }

    @GetMapping("/api/lookups/{resource}")
    public ApiResponse<PageResponse<Object>> lookup(
            @PathVariable String resource,
            @RequestParam(defaultValue = "") @Size(max = 160) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(5) int size,
            @RequestParam(required = false) String personType,
            @RequestParam(defaultValue = "") String purpose,
            @RequestParam(required = false) String contextId,
            Authentication authentication) {
        return new ApiResponse<>(service.search(resource, q, page, size, personType, null, purpose, contextId, true, authentication), "Lookup results");
    }
}
