package dev.lourencogabriel.inatead.document.repository;

import dev.lourencogabriel.inatead.document.model.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {

    boolean existsByStorageKey(String storageKey);
}
