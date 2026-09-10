package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.contract.repository.ContractDocumentRepository;
import dev.lourencogabriel.inatead.document.dto.DocumentTypeDtos;
import dev.lourencogabriel.inatead.document.model.DocumentType;
import dev.lourencogabriel.inatead.document.model.DocumentTypeScope;
import dev.lourencogabriel.inatead.document.repository.DocumentTypeRepository;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentTypeService {

    private final DocumentTypeRepository repository;
    private final PersonDocumentRepository personDocumentRepository;
    private final ContractDocumentRepository contractDocumentRepository;

    @Transactional
    public DocumentTypeDtos.Response create(DocumentTypeDtos.Request request) {
        DocumentType entity = new DocumentType();
        apply(entity, request, true);
        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public DocumentTypeDtos.Response findById(Short id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentTypeDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable).map(this::toResponse));
    }

    @Transactional
    public DocumentTypeDtos.Response update(Short id, DocumentTypeDtos.Request request) {
        DocumentType entity = require(id);
        apply(entity, request, false);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(Short id) {
        repository.delete(require(id));
    }

    private void apply(DocumentType entity, DocumentTypeDtos.Request request, boolean creating) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = creating
                ? repository.existsByCode(code)
                : repository.existsByCodeAndIdNot(code, entity.getId());
        if (duplicate) {
            throw new ResourceConflictException("A document type with this code already exists");
        }
        if (!creating) {
            validateScopeChange(entity, request.scope());
        }
        entity.setCode(code);
        entity.setName(request.name().trim());
        entity.setDescription(
                request.description() == null || request.description().isBlank()
                        ? null
                        : request.description().trim()
        );
        entity.setScope(request.scope());
    }

    private void validateScopeChange(
            DocumentType entity,
            DocumentTypeScope newScope
    ) {
        if (!newScope.supportsPersonDocument()
                && personDocumentRepository.existsByDocumentType_Id(entity.getId())) {
            throw new BusinessRuleException(
                    "Document type scope cannot exclude person documents while it is in use"
            );
        }
        if (!newScope.supportsContractDocument()
                && contractDocumentRepository.existsByDocumentType_Id(entity.getId())) {
            throw new BusinessRuleException(
                    "Document type scope cannot exclude contract documents while it is in use"
            );
        }
    }

    private DocumentType require(Short id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document type", String.valueOf(id)));
    }

    private DocumentTypeDtos.Response toResponse(DocumentType entity) {
        return new DocumentTypeDtos.Response(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getScope()
        );
    }
}
