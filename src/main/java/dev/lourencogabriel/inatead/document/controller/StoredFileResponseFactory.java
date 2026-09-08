package dev.lourencogabriel.inatead.document.controller;

import dev.lourencogabriel.inatead.document.service.StoredFileDownload;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

public final class StoredFileResponseFactory {

    private StoredFileResponseFactory() {
    }

    public static ResponseEntity<Resource> attachment(StoredFileDownload download) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }
}
