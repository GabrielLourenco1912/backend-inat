package dev.lourencogabriel.inatead.document.repository;

import dev.lourencogabriel.inatead.document.model.PersonDocument;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PersonDocumentRepository extends JpaRepository<PersonDocument, String> {

    Page<PersonDocument> findByPerson_Id(String personId, Pageable pageable);

    Page<PersonDocument> findByVerificationStatus(DocumentVerificationStatus status, Pageable pageable);

    Page<PersonDocument> findByPerson_IdAndVerificationStatus(
            String personId, DocumentVerificationStatus status, Pageable pageable
    );

    boolean existsByDocumentType_Id(Short documentTypeId);

    boolean existsByPerson_IdAndDocumentType_Id(String personId, Short documentTypeId);

    boolean existsByPerson_IdAndDocumentType_IdAndIdNot(
            String personId,
            Short documentTypeId,
            String id
    );

    boolean existsByDocumentType_IdAndDocumentNumberIgnoreCase(
            Short documentTypeId,
            String documentNumber
    );

    boolean existsByDocumentType_IdAndDocumentNumberIgnoreCaseAndIdNot(
            Short documentTypeId,
            String documentNumber,
            String id
    );

    @Query("""
            select document.id
            from PersonDocument document
            where document.verificationStatus = :status
              and document.expiresOn < :today
            order by document.expiresOn, document.id
            """)
    List<String> findExpirationCandidateIds(
            @Param("status") DocumentVerificationStatus status,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from PersonDocument document where document.id = :id")
    Optional<PersonDocument> findByIdForUpdate(@Param("id") String id);
}
