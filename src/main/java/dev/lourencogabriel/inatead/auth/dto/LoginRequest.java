package dev.lourencogabriel.inatead.auth.dto;

public record LoginRequest(
        String email,
        String password
) {}
