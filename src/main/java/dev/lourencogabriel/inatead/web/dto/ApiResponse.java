package dev.lourencogabriel.inatead.web.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiResponse<T>(
        Instant timestamp,
        HttpStatus status,
        String message,
        T data
) {
    public ApiResponse(T data, String message, HttpStatus status) {
        this(
                Instant.now(),
                status,
                message,
                data
        );
    }

    public ApiResponse(T data, String message) {
        this(
                Instant.now(),
                HttpStatus.OK,
                message,
                data
        );
    }
}
