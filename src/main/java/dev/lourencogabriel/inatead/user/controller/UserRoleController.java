package dev.lourencogabriel.inatead.user.controller;

import dev.lourencogabriel.inatead.user.dto.UserRoleDtos;
import dev.lourencogabriel.inatead.user.service.UserRoleService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/roles")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService service;

    @GetMapping
    @PreAuthorize("@domainAuthorization.isSelfOrHasAnyRole(authentication, #userId, 'ADMIN')")
    public ApiResponse<List<UserRoleDtos.Response>> findByUser(@PathVariable String userId) {
        return new ApiResponse<>(service.findByUser(userId), "User roles found");
    }

    @PostMapping
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<UserRoleDtos.Response> grant(
            @PathVariable String userId,
            @Valid @RequestBody UserRoleDtos.GrantRequest request,
            Authentication authentication
    ) {
        return new ApiResponse<>(
                service.grant(userId, request.roleId(), authentication.getName()),
                "Role granted"
        );
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
    public ApiResponse<Void> revoke(
            @PathVariable String userId,
            @PathVariable Short roleId
    ) {
        service.revoke(userId, roleId);
        return new ApiResponse<>(null, "Role revoked");
    }
}
