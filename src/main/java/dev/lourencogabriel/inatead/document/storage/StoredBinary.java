package dev.lourencogabriel.inatead.document.storage;

public record StoredBinary(
        String storageKey,
        long sizeBytes,
        String checksumSha256,
        boolean newlyCreated
) {
}
