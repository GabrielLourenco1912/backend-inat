package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.contract.repository.ContractDocumentRepository;
import dev.lourencogabriel.inatead.document.dto.DocumentTypeDtos;
import dev.lourencogabriel.inatead.document.model.DocumentType;
import dev.lourencogabriel.inatead.document.model.DocumentTypeScope;
import dev.lourencogabriel.inatead.document.repository.DocumentTypeRepository;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceTest {

    @Mock
    private DocumentTypeRepository repository;
    @Mock
    private PersonDocumentRepository personDocumentRepository;
    @Mock
    private ContractDocumentRepository contractDocumentRepository;

    private DocumentTypeService service;

    @BeforeEach
    void setUp() {
        service = new DocumentTypeService(
                repository,
                personDocumentRepository,
                contractDocumentRepository
        );
    }

    @Test
    void shouldPersistTheConfiguredScope() {
        when(repository.save(any(DocumentType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentTypeDtos.Response response = service.create(new DocumentTypeDtos.Request(
                "identity_document",
                "Documento de identificação",
                null,
                DocumentTypeScope.PERSON
        ));

        assertEquals("IDENTITY_DOCUMENT", response.code());
        assertEquals(DocumentTypeScope.PERSON, response.scope());
    }

    @Test
    void shouldNotExcludePersonDocumentsFromAnInUseType() {
        DocumentType type = type();
        when(repository.findById((short) 1)).thenReturn(Optional.of(type));
        when(personDocumentRepository.existsByDocumentType_Id((short) 1)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.update(
                (short) 1,
                request(DocumentTypeScope.CONTRACT)
        ));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotExcludeContractDocumentsFromAnInUseType() {
        DocumentType type = type();
        when(repository.findById((short) 1)).thenReturn(Optional.of(type));
        when(contractDocumentRepository.existsByDocumentType_Id((short) 1)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.update(
                (short) 1,
                request(DocumentTypeScope.PERSON)
        ));
        verify(repository, never()).save(any());
    }

    private DocumentType type() {
        DocumentType type = new DocumentType();
        type.setId((short) 1);
        type.setCode("GENERAL");
        type.setName("Geral");
        type.setScope(DocumentTypeScope.BOTH);
        return type;
    }

    private DocumentTypeDtos.Request request(DocumentTypeScope scope) {
        return new DocumentTypeDtos.Request("GENERAL", "Geral", null, scope);
    }
}
