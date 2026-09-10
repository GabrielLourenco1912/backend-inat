package dev.lourencogabriel.inatead.contract.service;

import dev.lourencogabriel.inatead.contract.dto.ContractDocumentDtos;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.model.ContractDocument;
import dev.lourencogabriel.inatead.contract.repository.ContractDocumentRepository;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.document.model.DocumentType;
import dev.lourencogabriel.inatead.document.model.DocumentTypeScope;
import dev.lourencogabriel.inatead.document.model.StoredFile;
import dev.lourencogabriel.inatead.document.repository.DocumentTypeRepository;
import dev.lourencogabriel.inatead.document.service.StoredFileService;
import dev.lourencogabriel.inatead.document.storage.FileUploadPurpose;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractDocumentServiceTest {

    @Mock
    private ContractDocumentRepository documentRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private StoredFileService storedFileService;
    @Mock
    private MockMultipartFile upload;

    private ContractDocumentService service;

    @BeforeEach
    void setUp() {
        service = new ContractDocumentService(
                documentRepository,
                contractRepository,
                documentTypeRepository,
                storedFileService
        );
    }

    @Test
    void shouldStoreDocumentForEditableContractAndCompatibleType() {
        stubReferences(ContractStatus.ACTIVE, DocumentTypeScope.CONTRACT);
        StoredFile file = new StoredFile();
        file.setId("file-1");
        when(storedFileService.store(
                upload,
                "actor-1",
                FileUploadPurpose.CONTRACT_DOCUMENT
        )).thenReturn(file);
        when(documentRepository.save(any(ContractDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContractDocumentDtos.Response response = service.create(request(), upload, "actor-1");

        assertEquals("contract-1", response.contractId());
        verify(storedFileService).store(
                upload,
                "actor-1",
                FileUploadPurpose.CONTRACT_DOCUMENT
        );
    }

    @Test
    void shouldRejectDocumentsForEndedOrCancelledContract() {
        for (ContractStatus status : new ContractStatus[]{
                ContractStatus.ENDED,
                ContractStatus.CANCELLED
        }) {
            stubReferences(status, DocumentTypeScope.CONTRACT);
            assertThrows(BusinessRuleException.class, () -> service.create(
                    request(),
                    upload,
                    "actor-1"
            ));
        }
        verify(storedFileService, never()).store(any(), any(), any());
    }

    @Test
    void shouldRejectPersonOnlyDocumentType() {
        stubReferences(ContractStatus.ACTIVE, DocumentTypeScope.PERSON);

        assertThrows(BusinessRuleException.class, () -> service.create(
                request(),
                upload,
                "actor-1"
        ));
    }

    @Test
    void shouldRejectUpdatesForCancelledContract() {
        Contract contract = new Contract();
        contract.setId("contract-1");
        contract.setStatus(ContractStatus.CANCELLED);
        DocumentType type = new DocumentType();
        type.setId((short) 1);
        type.setScope(DocumentTypeScope.CONTRACT);
        ContractDocument document = new ContractDocument();
        document.setId("document-1");
        document.setContract(contract);
        document.setDocumentType(type);
        when(documentRepository.findById("document-1")).thenReturn(Optional.of(document));
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
        when(documentTypeRepository.findById((short) 1)).thenReturn(Optional.of(type));

        assertThrows(BusinessRuleException.class, () -> service.update(
                "document-1",
                request()
        ));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldPreserveDocumentsFromEndedContracts() {
        Contract contract = new Contract();
        contract.setStatus(ContractStatus.ENDED);
        ContractDocument document = new ContractDocument();
        document.setContract(contract);
        when(documentRepository.findById("document-1")).thenReturn(Optional.of(document));

        assertThrows(BusinessRuleException.class, () -> service.delete("document-1"));

        verify(documentRepository, never()).delete(any());
        verify(storedFileService, never()).delete(any());
    }

    private void stubReferences(ContractStatus status, DocumentTypeScope scope) {
        Contract contract = new Contract();
        contract.setId("contract-1");
        contract.setStatus(status);
        DocumentType type = new DocumentType();
        type.setId((short) 1);
        type.setScope(scope);
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
        when(documentTypeRepository.findById((short) 1)).thenReturn(Optional.of(type));
    }

    private ContractDocumentDtos.Request request() {
        return new ContractDocumentDtos.Request("contract-1", (short) 1, 1, true);
    }
}
