package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.document.dto.PersonDocumentDtos;
import dev.lourencogabriel.inatead.document.model.DocumentType;
import dev.lourencogabriel.inatead.document.model.DocumentTypeScope;
import dev.lourencogabriel.inatead.document.model.PersonDocument;
import dev.lourencogabriel.inatead.document.model.PersonDocumentStatusChangeReason;
import dev.lourencogabriel.inatead.document.model.StoredFile;
import dev.lourencogabriel.inatead.document.repository.DocumentTypeRepository;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.document.storage.FileUploadPurpose;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonDocumentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Mock
    private PersonDocumentRepository documentRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoredFileService storedFileService;
    @Mock
    private MockMultipartFile upload;

    private PersonDocumentService service;

    @BeforeEach
    void setUp() {
        service = new PersonDocumentService(
                documentRepository,
                personRepository,
                documentTypeRepository,
                userRepository,
                storedFileService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateDocumentAsVerifiedAndRecordUploadHistory() {
        Person person = person(RecordStatus.ACTIVE);
        DocumentType type = type(DocumentTypeScope.PERSON);
        User actor = actor();
        StoredFile file = file();
        stubReferences(person, type);
        when(userRepository.findById("actor-1")).thenReturn(Optional.of(actor));
        when(storedFileService.store(
                upload,
                "actor-1",
                FileUploadPurpose.PERSON_DOCUMENT
        )).thenReturn(file);
        when(documentRepository.save(any(PersonDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonDocumentDtos.Response response = service.create(
                request(TODAY.minusYears(1), TODAY.plusYears(1)),
                upload,
                "actor-1"
        );

        assertEquals(DocumentVerificationStatus.VERIFIED, response.verificationStatus());
        assertEquals("actor-1", response.verifiedByUserId());
        assertEquals(NOW, response.verifiedAt());
        assertEquals(1, response.statusHistory().size());
        assertEquals(
                PersonDocumentStatusChangeReason.ADMIN_UPLOAD,
                response.statusHistory().getFirst().changeReason()
        );
    }

    @Test
    void shouldRejectFutureIssueDateAndAlreadyExpiredDate() {
        stubReferences(person(RecordStatus.ACTIVE), type(DocumentTypeScope.PERSON));

        assertThrows(BusinessRuleException.class, () -> service.create(
                request(TODAY.plusDays(1), TODAY.plusYears(1)),
                upload,
                "actor-1"
        ));
        assertThrows(BusinessRuleException.class, () -> service.create(
                request(TODAY.minusYears(1), TODAY.minusDays(1)),
                upload,
                "actor-1"
        ));
        verify(storedFileService, never()).store(any(), any(), any());
    }

    @Test
    void shouldRejectInactivePersonAndContractOnlyType() {
        stubReferences(person(RecordStatus.INACTIVE), type(DocumentTypeScope.PERSON));
        assertThrows(BusinessRuleException.class, () -> service.create(
                request(null, null),
                upload,
                "actor-1"
        ));

        Person activePerson = person(RecordStatus.ACTIVE);
        DocumentType contractType = type(DocumentTypeScope.CONTRACT);
        stubReferences(activePerson, contractType);
        assertThrows(BusinessRuleException.class, () -> service.create(
                request(null, null),
                upload,
                "actor-1"
        ));
    }

    @Test
    void shouldRejectSecondDocumentOfSameTypeForPerson() {
        stubReferences(person(RecordStatus.ACTIVE), type(DocumentTypeScope.PERSON));
        when(documentRepository.existsByPerson_IdAndDocumentType_Id("person-1", (short) 1))
                .thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.create(
                request(null, null),
                upload,
                "actor-1"
        ));
    }

    @Test
    void shouldRejectDocumentNumberAlreadyUsedBySameType() {
        stubReferences(person(RecordStatus.ACTIVE), type(DocumentTypeScope.PERSON));
        when(documentRepository.existsByDocumentType_IdAndDocumentNumberIgnoreCase(
                (short) 1,
                "ABC-123"
        )).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.create(
                request(null, null),
                upload,
                "actor-1"
        ));
    }

    @Test
    void shouldRenewExpiredDocumentAndRecordStatusHistory() {
        Person person = person(RecordStatus.ACTIVE);
        DocumentType type = type(DocumentTypeScope.PERSON);
        User actor = actor();
        PersonDocument document = new PersonDocument();
        document.setId("document-1");
        document.setPerson(person);
        document.setDocumentType(type);
        document.setFile(file());
        document.setVerificationStatus(DocumentVerificationStatus.EXPIRED);
        document.setVerifiedByUser(actor);
        document.setVerifiedAt(NOW.minusSeconds(3600));
        when(documentRepository.findById("document-1")).thenReturn(Optional.of(document));
        stubReferences(person, type);
        when(userRepository.findById("actor-1")).thenReturn(Optional.of(actor));
        when(documentRepository.save(document)).thenReturn(document);

        PersonDocumentDtos.Response response = service.update(
                "document-1",
                request(TODAY.minusYears(1), TODAY.plusYears(1)),
                "actor-1"
        );

        assertEquals(DocumentVerificationStatus.VERIFIED, response.verificationStatus());
        assertEquals(
                PersonDocumentStatusChangeReason.ADMIN_RENEWAL,
                response.statusHistory().getFirst().changeReason()
        );
    }

    @Test
    void shouldNotMoveAnExistingDocumentToAnotherPerson() {
        PersonDocument document = new PersonDocument();
        document.setId("document-1");
        document.setPerson(person(RecordStatus.INACTIVE));
        document.setDocumentType(type(DocumentTypeScope.PERSON));
        when(documentRepository.findById("document-1")).thenReturn(Optional.of(document));

        PersonDocumentDtos.Request request = new PersonDocumentDtos.Request(
                "person-2",
                (short) 1,
                "ABC-123",
                TODAY.minusYears(1),
                TODAY.plusYears(1)
        );

        assertThrows(BusinessRuleException.class, () -> service.update(
                "document-1",
                request,
                "actor-1"
        ));
        verify(personRepository, never()).findById("person-2");
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldRejectMetadataUpdatesForAnInactivePerson() {
        Person inactivePerson = person(RecordStatus.INACTIVE);
        DocumentType type = type(DocumentTypeScope.PERSON);
        PersonDocument document = new PersonDocument();
        document.setId("document-1");
        document.setPerson(inactivePerson);
        document.setDocumentType(type);
        when(documentRepository.findById("document-1")).thenReturn(Optional.of(document));
        stubReferences(inactivePerson, type);

        assertThrows(BusinessRuleException.class, () -> service.update(
                "document-1",
                request(TODAY.minusYears(1), TODAY.plusYears(1)),
                "actor-1"
        ));
        verify(documentRepository, never()).save(any());
    }

    private void stubReferences(Person person, DocumentType type) {
        when(personRepository.findById("person-1")).thenReturn(Optional.of(person));
        when(documentTypeRepository.findById((short) 1)).thenReturn(Optional.of(type));
    }

    private PersonDocumentDtos.Request request(LocalDate issuedOn, LocalDate expiresOn) {
        return new PersonDocumentDtos.Request(
                "person-1",
                (short) 1,
                "ABC-123",
                issuedOn,
                expiresOn
        );
    }

    private Person person(RecordStatus status) {
        Person person = new Person();
        person.setId("person-1");
        person.setStatus(status);
        return person;
    }

    private DocumentType type(DocumentTypeScope scope) {
        DocumentType type = new DocumentType();
        type.setId((short) 1);
        type.setScope(scope);
        return type;
    }

    private User actor() {
        User actor = new User();
        actor.setUlid("actor-1");
        return actor;
    }

    private StoredFile file() {
        StoredFile file = new StoredFile();
        file.setId("file-1");
        return file;
    }
}
