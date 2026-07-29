package dev.lourencogabriel.wmssaas.auth.dto;

public record LoginRequest(
        String email,
        String password
) {}
