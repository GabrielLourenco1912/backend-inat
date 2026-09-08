package dev.lourencogabriel.inatead.user.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.user.dto.CurrentUserContextResponse;
import dev.lourencogabriel.inatead.user.service.CurrentUserContextService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class CurrentUserContextController {

    private final CurrentUserContextService service;
    private final DomainAuthorization authorization;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CurrentUserContextResponse> find(Authentication authentication) {
        return new ApiResponse<>(
                this.service.find(this.authorization.userId(authentication)),
                "Current user context found"
        );
    }
}
