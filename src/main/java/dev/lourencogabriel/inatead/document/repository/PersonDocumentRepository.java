package dev.lourencogabriel.inatead.document.repository;

import dev.lourencogabriel.inatead.document.model.PersonDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonDocumentRepository extends JpaRepository<PersonDocument, String> {
}
