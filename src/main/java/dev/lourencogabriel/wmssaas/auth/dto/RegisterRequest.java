package dev.lourencogabriel.wmssaas.auth.dto;

public record RegisterRequest(
        String name,
        String email,
        String password
) {}
