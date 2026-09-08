package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.document.dto.PersonDocumentDtos;
import dev.lourencogabriel.inatead.document.model.PersonDocument;
import dev.lourencogabriel.inatead.document.repository.DocumentTypeRepository;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PersonDocumentService {

    private final PersonDocumentRepository documentRepository;
    private final PersonRepository personRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final UserRepository userRepository;
    private final StoredFileService storedFileService;

    @Transactional
    public PersonDocumentDtos.Response create(
            PersonDocumentDtos.Request request,
            MultipartFile upload,
            String actorUserId
    ) {
        PersonDocument document = new PersonDocument();
        apply(document, request);
        applyVerificationAudit(document, null, actorUserId);
        validate(document);
        document.setFile(storedFileService.store(upload, actorUserId));
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

    @Transactional
    public PersonDocumentDtos.Response update(
            String id,
            PersonDocumentDtos.Request request,
            String actorUserId
    ) {
        PersonDocument document = require(id);
        DocumentVerificationStatus previousStatus = document.getVerificationStatus();
        apply(document, request);
        applyVerificationAudit(document, previousStatus, actorUserId);
        validate(document);
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
        document.setVerificationStatus(request.verificationStatus());
    }

    private void applyVerificationAudit(
            PersonDocument document,
            DocumentVerificationStatus previousStatus,
            String actorUserId
    ) {
        if (document.getVerificationStatus() == DocumentVerificationStatus.PENDING) {
            document.setVerifiedByUser(null);
            document.setVerifiedAt(null);
            return;
        }
        if (document.getVerificationStatus() != previousStatus
                || document.getVerifiedByUser() == null
                || document.getVerifiedAt() == null) {
            document.setVerifiedByUser(EntityLookup.require(
                    userRepository,
                    actorUserId,
                    "Verifying user"
            ));
            document.setVerifiedAt(Instant.now());
        }
    }

    private void validate(PersonDocument document) {
        if (document.getIssuedOn() != null
                && document.getExpiresOn() != null
                && document.getExpiresOn().isBefore(document.getIssuedOn())) {
            throw new BusinessRuleException("Document expiration cannot precede its issue date");
        }
        boolean pending = document.getVerificationStatus() == DocumentVerificationStatus.PENDING;
        boolean hasVerifier = document.getVerifiedByUser() != null;
        boolean hasVerificationTime = document.getVerifiedAt() != null;
        if (pending && (hasVerifier || hasVerificationTime)
                || !pending && (!hasVerifier || !hasVerificationTime)) {
            throw new BusinessRuleException(
                    "Pending documents cannot have verification audit data, and reviewed documents require it"
            );
        }
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
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
