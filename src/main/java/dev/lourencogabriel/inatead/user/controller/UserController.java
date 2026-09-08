package dev.lourencogabriel.inatead.user.controller;

import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.user.dto.UserDtos;
import dev.lourencogabriel.inatead.user.dto.UserResponse;
import dev.lourencogabriel.inatead.user.service.UserService;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService service;

    @PostMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return new ApiResponse<>(service.create(request), "User created");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@domainAuthorization.isSelfOrHasAnyRole(authentication, #id, 'ADMIN')")
    public ApiResponse<UserResponse> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "User found");
    }

    @GetMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(service.findAll(PageRequest.of(page, size)), "Users found");
    }

    @PutMapping("/{id}")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<UserResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UserDtos.UpdateRequest request
    ) {
        return new ApiResponse<>(service.update(id, request), "User updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "User deleted");
    }
}
