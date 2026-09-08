package dev.lourencogabriel.inatead.document.service;

import org.springframework.core.io.Resource;

public record StoredFileDownload(
        Resource resource,
        String originalName,
        String mimeType,
        long sizeBytes
) {
}
