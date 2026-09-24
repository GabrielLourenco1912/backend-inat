package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.document.dto.PersonDocumentDtos;
import dev.lourencogabriel.inatead.document.model.PersonDocument;
import dev.lourencogabriel.inatead.document.model.PersonDocumentStatusChangeReason;
import dev.lourencogabriel.inatead.document.model.PersonDocumentStatusHistory;
import dev.lourencogabriel.inatead.document.repository.DocumentTypeRepository;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.document.storage.FileUploadPurpose;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PersonDocumentService {

    private final PersonDocumentRepository documentRepository;
    private final PersonRepository personRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final UserRepository userRepository;
    private final StoredFileService storedFileService;
    private final Clock clock;

    @Transactional
    public PersonDocumentDtos.Response create(
            PersonDocumentDtos.Request request,
            MultipartFile upload,
            String actorUserId
    ) {
        PersonDocument document = new PersonDocument();
        apply(document, request);
        validate(document, true);
        User actor = EntityLookup.require(userRepository, actorUserId, "Verifying user");
        Instant now = Instant.now(clock);
        document.setVerificationStatus(DocumentVerificationStatus.VERIFIED);
        document.setVerifiedByUser(actor);
        document.setVerifiedAt(now);
        addStatusHistory(
                document,
                null,
                DocumentVerificationStatus.VERIFIED,
                PersonDocumentStatusChangeReason.ADMIN_UPLOAD,
                actor,
                now
        );
        document.setFile(storedFileService.store(
                upload,
                actorUserId,
                FileUploadPurpose.PERSON_DOCUMENT
        ));
        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public PersonDocumentDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonDocumentDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(documentRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonDocumentDtos.Response> findAll(
            String personId, DocumentVerificationStatus verificationStatus, Pageable pageable
    ) {
        Page<PersonDocument> documents;
        if (personId != null && verificationStatus != null) {
            documents = documentRepository.findByPerson_IdAndVerificationStatus(
                    personId, verificationStatus, pageable
            );
        } else if (personId != null) {
            documents = documentRepository.findByPerson_Id(personId, pageable);
        } else if (verificationStatus != null) {
            documents = documentRepository.findByVerificationStatus(verificationStatus, pageable);
        } else {
            documents = documentRepository.findAll(pageable);
        }
        return PageResponse.from(documents.map(this::toResponse));
    }

    @Transactional
    public PersonDocumentDtos.Response update(
            String id,
            PersonDocumentDtos.Request request,
            String actorUserId
    ) {
        PersonDocument document = require(id);
        ensureIdentityIsUnchanged(document, request);
        DocumentVerificationStatus previousStatus = document.getVerificationStatus();
        apply(document, request);
        validate(document, false);
        if (previousStatus == DocumentVerificationStatus.EXPIRED) {
            User actor = EntityLookup.require(userRepository, actorUserId, "Verifying user");
            Instant now = Instant.now(clock);
            document.setVerificationStatus(DocumentVerificationStatus.VERIFIED);
            document.setVerifiedByUser(actor);
            document.setVerifiedAt(now);
            addStatusHistory(
                    document,
                    previousStatus,
                    DocumentVerificationStatus.VERIFIED,
                    PersonDocumentStatusChangeReason.ADMIN_RENEWAL,
                    actor,
                    now
            );
        }
        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public StoredFileDownload download(String id) {
        return storedFileService.download(require(id).getFile());
    }

    @Transactional
    public void delete(String id) {
        PersonDocument document = require(id);
        var file = document.getFile();
        documentRepository.delete(document);
        documentRepository.flush();
        storedFileService.delete(file);
    }

    private void apply(PersonDocument document, PersonDocumentDtos.Request request) {
        document.setPerson(EntityLookup.require(personRepository, request.personId(), "Person"));
        document.setDocumentType(EntityLookup.require(
                documentTypeRepository,
                request.documentTypeId(),
                "Document type"
        ));
        document.setDocumentNumber(normalize(request.documentNumber()));
        document.setIssuedOn(request.issuedOn());
        document.setExpiresOn(request.expiresOn());
    }

    private void validate(PersonDocument document, boolean creating) {
        if (document.getPerson().getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("Documents can only be linked to an active person");
        }
        if (!document.getDocumentType().getScope().supportsPersonDocument()) {
            throw new BusinessRuleException("Document type is not allowed for person documents");
        }

        LocalDate today = BusinessTime.today(clock);
        if (document.getIssuedOn() != null && document.getIssuedOn().isAfter(today)) {
            throw new BusinessRuleException("Document issue date cannot be in the future");
        }
        if (document.getExpiresOn() != null && document.getExpiresOn().isBefore(today)) {
            throw new BusinessRuleException("Document expiration date cannot already be past");
        }
        if (document.getIssuedOn() != null
                && document.getExpiresOn() != null
                && document.getExpiresOn().isBefore(document.getIssuedOn())) {
            throw new BusinessRuleException("Document expiration cannot precede its issue date");
        }

        boolean duplicateType = creating
                ? documentRepository.existsByPerson_IdAndDocumentType_Id(
                        document.getPerson().getId(),
                        document.getDocumentType().getId()
                )
                : documentRepository.existsByPerson_IdAndDocumentType_IdAndIdNot(
                        document.getPerson().getId(),
                        document.getDocumentType().getId(),
                        document.getId()
                );
        if (duplicateType) {
            throw new ResourceConflictException(
                    "The person already has a document of this type"
            );
        }

        if (document.getDocumentNumber() != null) {
            boolean duplicateNumber = creating
                    ? documentRepository.existsByDocumentType_IdAndDocumentNumberIgnoreCase(
                            document.getDocumentType().getId(),
                            document.getDocumentNumber()
                    )
                    : documentRepository
                            .existsByDocumentType_IdAndDocumentNumberIgnoreCaseAndIdNot(
                                    document.getDocumentType().getId(),
                                    document.getDocumentNumber(),
                                    document.getId()
                            );
            if (duplicateNumber) {
                throw new ResourceConflictException(
                        "A document of this type already uses this number"
                );
            }
        }
    }

    private void ensureIdentityIsUnchanged(
            PersonDocument document,
            PersonDocumentDtos.Request request
    ) {
        if (!document.getPerson().getId().equals(request.personId())) {
            throw new BusinessRuleException("personId cannot be changed");
        }
        if (!document.getDocumentType().getId().equals(request.documentTypeId())) {
            throw new BusinessRuleException("documentTypeId cannot be changed");
        }
    }

    private void addStatusHistory(
            PersonDocument document,
            DocumentVerificationStatus previousStatus,
            DocumentVerificationStatus newStatus,
            PersonDocumentStatusChangeReason reason,
            User actor,
            Instant changedAt
    ) {
        PersonDocumentStatusHistory history = new PersonDocumentStatusHistory();
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangeReason(reason);
        history.setChangedByUser(actor);
        history.setChangedAt(changedAt);
        document.addStatusHistory(history);
    }

    private PersonDocument require(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person document", id));
    }

    private PersonDocumentDtos.Response toResponse(PersonDocument document) {
        return new PersonDocumentDtos.Response(
                document.getId(),
                document.getPerson().getId(),
                storedFileService.toResponse(document.getFile()),
                document.getDocumentType().getId(),
                document.getDocumentNumber(),
                document.getIssuedOn(),
                document.getExpiresOn(),
                document.getVerificationStatus(),
                document.getVerifiedByUser() == null
                        ? null
                        : document.getVerifiedByUser().getUlid(),
                document.getVerifiedAt(),
                document.getStatusHistory().stream()
                        .map(history -> new PersonDocumentDtos.StatusHistoryResponse(
                                history.getId(),
                                history.getPreviousStatus(),
                                history.getNewStatus(),
                                history.getChangeReason(),
                                history.getChangedByUser() == null
                                        ? null
                                        : history.getChangedByUser().getUlid(),
                                history.getChangedAt()
                        ))
                        .toList(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
