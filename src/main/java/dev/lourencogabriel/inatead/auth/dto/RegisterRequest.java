package dev.lourencogabriel.inatead.auth.dto;

public record RegisterRequest(
        String name,
        String email,
        String password
) {}
