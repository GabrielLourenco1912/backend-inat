package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.document.repository.StoredFileRepository;
import dev.lourencogabriel.inatead.document.storage.FileSystemStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoredFileCleanupService {

    private final StoredFileRepository fileRepository;
    private final FileSystemStorage storage;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void deleteBinaryIfUnreferenced(String storageKey, String operation) {
        if (fileRepository.existsByStorageKey(storageKey)) {
            return;
        }
        try {
            storage.delete(storageKey);
        } catch (RuntimeException exception) {
            log.error("Could not clean file content after {}", operation, exception);
        }
    }
}
