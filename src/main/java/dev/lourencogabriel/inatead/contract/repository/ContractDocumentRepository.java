package dev.lourencogabriel.inatead.contract.repository;

import dev.lourencogabriel.inatead.contract.model.ContractDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, String> {

    boolean existsByContract_IdAndDocumentType_IdAndVersionNumber(
            String contractId,
            Short documentTypeId,
            int versionNumber
    );

    boolean existsByContract_IdAndDocumentType_IdAndVersionNumberAndIdNot(
            String contractId,
            Short documentTypeId,
            int versionNumber,
            String id
    );

    boolean existsByContract_IdAndDocumentType_IdAndCurrentTrue(
            String contractId,
            Short documentTypeId
    );

    boolean existsByContract_IdAndDocumentType_IdAndCurrentTrueAndIdNot(
            String contractId,
            Short documentTypeId,
            String id
    );
}
