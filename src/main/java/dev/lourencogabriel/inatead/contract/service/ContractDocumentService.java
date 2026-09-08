package dev.lourencogabriel.inatead.contract.service;

import dev.lourencogabriel.inatead.contract.dto.ContractDocumentDtos;
import dev.lourencogabriel.inatead.contract.model.ContractDocument;
import dev.lourencogabriel.inatead.contract.repository.ContractDocumentRepository;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.document.repository.DocumentTypeRepository;
import dev.lourencogabriel.inatead.document.service.StoredFileDownload;
import dev.lourencogabriel.inatead.document.service.StoredFileService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ContractDocumentService {

    private final ContractDocumentRepository documentRepository;
    private final ContractRepository contractRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final StoredFileService storedFileService;

    @Transactional
    public ContractDocumentDtos.Response create(
            ContractDocumentDtos.Request request,
            MultipartFile upload,
            String actorUserId
    ) {
        ContractDocument document = new ContractDocument();
        apply(document, request);
        validate(document, true);
        document.setFile(storedFileService.store(upload, actorUserId));
        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public ContractDocumentDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractDocumentDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(documentRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional
    public ContractDocumentDtos.Response update(
            String id,
            ContractDocumentDtos.Request request
    ) {
        ContractDocument document = require(id);
        apply(document, request);
        validate(document, false);
        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public StoredFileDownload download(String id) {
        return storedFileService.download(require(id).getFile());
    }

    @Transactional
    public void delete(String id) {
        ContractDocument document = require(id);
        var file = document.getFile();
        documentRepository.delete(document);
        documentRepository.flush();
        storedFileService.delete(file);
    }

    private void apply(ContractDocument document, ContractDocumentDtos.Request request) {
        document.setContract(EntityLookup.require(
                contractRepository,
                request.contractId(),
                "Contract"
        ));
        document.setDocumentType(EntityLookup.require(
                documentTypeRepository,
                request.documentTypeId(),
                "Document type"
        ));
        document.setVersionNumber(request.versionNumber());
        document.setCurrent(request.current());
    }

    private void validate(ContractDocument document, boolean creating) {
        boolean duplicateVersion = creating
                ? documentRepository.existsByContract_IdAndDocumentType_IdAndVersionNumber(
                        document.getContract().getId(),
                        document.getDocumentType().getId(),
                        document.getVersionNumber()
                )
                : documentRepository
                        .existsByContract_IdAndDocumentType_IdAndVersionNumberAndIdNot(
                                document.getContract().getId(),
                                document.getDocumentType().getId(),
                                document.getVersionNumber(),
                                document.getId()
                        );
        if (duplicateVersion) {
            throw new ResourceConflictException("This contract document version already exists");
        }

        if (document.isCurrent()) {
            boolean anotherCurrent = creating
                    ? documentRepository.existsByContract_IdAndDocumentType_IdAndCurrentTrue(
                            document.getContract().getId(),
                            document.getDocumentType().getId()
                    )
                    : documentRepository
                            .existsByContract_IdAndDocumentType_IdAndCurrentTrueAndIdNot(
                                    document.getContract().getId(),
                                    document.getDocumentType().getId(),
                                    document.getId()
                            );
            if (anotherCurrent) {
                throw new BusinessRuleException(
                        "Only one current document is allowed per contract and document type"
                );
            }
        }
    }

    private ContractDocument require(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract document", id));
    }

    private ContractDocumentDtos.Response toResponse(ContractDocument document) {
        return new ContractDocumentDtos.Response(
                document.getId(),
                document.getContract().getId(),
                storedFileService.toResponse(document.getFile()),
                document.getDocumentType().getId(),
                document.getVersionNumber(),
                document.isCurrent(),
                document.getCreatedAt()
        );
    }
}
