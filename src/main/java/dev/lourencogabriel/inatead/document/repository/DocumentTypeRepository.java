package dev.lourencogabriel.inatead.document.repository;

import dev.lourencogabriel.inatead.document.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Short> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Short id);
}
