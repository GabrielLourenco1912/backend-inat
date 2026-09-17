package dev.lourencogabriel.inatead.document;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.model.ContractDocument;
import dev.lourencogabriel.inatead.contract.service.ContractDocumentService;
import dev.lourencogabriel.inatead.document.model.DocumentType;
import dev.lourencogabriel.inatead.document.model.DocumentTypeScope;
import dev.lourencogabriel.inatead.document.model.PersonDocument;
import dev.lourencogabriel.inatead.document.model.StoredFile;
import dev.lourencogabriel.inatead.document.service.PersonDocumentService;
import dev.lourencogabriel.inatead.learner.dto.LearnerDtos;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.service.LearnerService;
import dev.lourencogabriel.inatead.organization.dto.OrganizationDtos;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.service.OrganizationService;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:document_listing_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class DocumentListingIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(810000);
    private final Map<String, User> verifyingUsers = new HashMap<>();
    @Autowired private EntityManager entityManager;
    @Autowired private PersonService people;
    @Autowired private LearnerService learners;
    @Autowired private OrganizationService organizations;
    @Autowired private PersonDocumentService personDocuments;
    @Autowired private ContractDocumentService contractDocuments;

    @Test
    void shouldFilterExpiredDocumentsBeforePaginationAndOrderByExpiration() {
        Person person = person();
        document(person, DocumentVerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1));
        PersonDocument later = document(person, DocumentVerificationStatus.EXPIRED, LocalDate.of(2026, 8, 1));
        PersonDocument earlier = document(person(), DocumentVerificationStatus.EXPIRED, LocalDate.of(2026, 7, 1));
        entityManager.flush();
        entityManager.clear();
        Sort sort = Sort.by("expiresOn").and(Sort.by("id"));

        var first = personDocuments.findAll(null, DocumentVerificationStatus.EXPIRED, PageRequest.of(0, 1, sort));
        var second = personDocuments.findAll(null, DocumentVerificationStatus.EXPIRED, PageRequest.of(1, 1, sort));

        assertEquals(2, first.totalElements());
        assertEquals(2, first.totalPages());
        assertEquals(earlier.getId(), first.content().getFirst().id());
        assertEquals(later.getId(), second.content().getFirst().id());
        assertTrue(second.last());
    }

    @Test
    void shouldScopeDocumentsToPersonAndIntersectOptionalStatus() {
        Person owner = person();
        var verified = document(owner, DocumentVerificationStatus.VERIFIED, null);
        var expired = document(owner, DocumentVerificationStatus.EXPIRED, LocalDate.of(2026, 1, 1));
        document(person(), DocumentVerificationStatus.EXPIRED, LocalDate.of(2026, 1, 1));
        entityManager.flush();
        entityManager.clear();

        var page = personDocuments.findAll(owner.getId(), null, PageRequest.of(0, 1));
        assertEquals(2, page.totalElements());
        assertEquals(owner.getId(), page.content().getFirst().personId());
        var expiredPage = personDocuments.findAll(owner.getId(), DocumentVerificationStatus.EXPIRED, PageRequest.of(0, 20));
        assertEquals(1, expiredPage.totalElements());
        assertEquals(expired.getId(), expiredPage.content().getFirst().id());
        var verifiedPage = personDocuments.findAll(owner.getId(), DocumentVerificationStatus.VERIFIED, PageRequest.of(0, 20));
        assertEquals(verified.getId(), verifiedPage.content().getFirst().id());
        assertEquals(0, personDocuments.findAll("missing-person", null, PageRequest.of(0, 20)).totalElements());
        assertEquals(3, personDocuments.findAll(null, null, PageRequest.of(0, 20)).totalElements());
    }

    @Test
    void shouldListOnlyDocumentsOfTheContractIncludingItsVersions() {
        Person person = person();
        Learner learner = learner(person);
        Contract first = contract(learner);
        Contract other = contract(learner);
        DocumentType type = type();
        contractDocument(first, type, 1);
        contractDocument(first, type, 2);
        contractDocument(other, type, 1);
        document(person, DocumentVerificationStatus.VERIFIED, null);
        entityManager.flush();
        entityManager.clear();

        var page = contractDocuments.findAll(first.getId(), PageRequest.of(0, 1, Sort.by("versionNumber")));
        var secondPage = contractDocuments.findAll(first.getId(), PageRequest.of(1, 1, Sort.by("versionNumber")));
        assertEquals(2, page.totalElements());
        assertEquals(1, page.content().getFirst().versionNumber());
        assertEquals(first.getId(), page.content().getFirst().contractId());
        assertEquals(2, secondPage.content().getFirst().versionNumber());
        assertEquals(first.getId(), secondPage.content().getFirst().contractId());
        assertEquals(0, contractDocuments.findAll("missing-contract", PageRequest.of(0, 20)).totalElements());
        assertEquals(3, contractDocuments.findAll(null, PageRequest.of(0, 20)).totalElements());
    }

    @Test
    void shouldResolveLearnerByPersonWithoutLoadingAllLearners() {
        Person person = person();
        Learner learner = learner(person);
        learner(person());
        entityManager.flush();
        entityManager.clear();

        var page = learners.findByPerson(person.getId(), PageRequest.of(0, 1));
        assertEquals(1, page.totalElements());
        assertEquals(learner.getId(), page.content().getFirst().id());
        assertEquals(0, learners.findByPerson("missing-person", PageRequest.of(0, 1)).totalElements());
    }

    private Person person() {
        var response = people.create(new PersonDtos.Request(address(), "Document owner",
                String.format("%011d", SEQUENCE.incrementAndGet()), null, "41999999999",
                LocalDate.of(1990, 1, 1), null, RecordStatus.ACTIVE, Set.of(PersonTypeCode.LEARNER)));
        return entityManager.find(Person.class, response.id());
    }

    private Learner learner(Person person) {
        var response = learners.create(new LearnerDtos.Request(person.getId(),
                "DOC-" + SEQUENCE.incrementAndGet(), true, RecordStatus.ACTIVE));
        return entityManager.find(Learner.class, response.id());
    }

    private PersonDocument document(Person person, DocumentVerificationStatus status, LocalDate expiresOn) {
        User actor = verifyingUsers.computeIfAbsent(person.getId(), ignored -> {
            User user = new User("Document administrator", SEQUENCE.incrementAndGet() + "@docs.test", "hash");
            user.setPerson(person);
            entityManager.persist(user);
            return user;
        });
        PersonDocument document = new PersonDocument();
        document.setPerson(person);
        document.setDocumentType(type());
        document.setFile(file());
        document.setVerificationStatus(status);
        document.setExpiresOn(expiresOn);
        document.setVerifiedByUser(actor);
        document.setVerifiedAt(Instant.parse("2026-01-01T12:00:00Z"));
        entityManager.persist(document);
        return document;
    }

    private Contract contract(Learner learner) {
        var organization = organizations.create(new OrganizationDtos.Request(null, address(), OrganizationType.EMPLOYER,
                "Document employer", null, String.format("%014d", SEQUENCE.incrementAndGet()),
                "employer@docs.test", "41999999999", null, RecordStatus.ACTIVE));
        Contract contract = new Contract();
        contract.setLearner(learner);
        contract.setEmployer(entityManager.find(Organization.class, organization.id()));
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setMonthlySalary(BigDecimal.valueOf(1000));
        contract.setWeeklyWorkloadMinutes(1200);
        entityManager.persist(contract);
        return contract;
    }

    private void contractDocument(Contract contract, DocumentType type, int version) {
        ContractDocument document = new ContractDocument();
        document.setContract(contract);
        document.setDocumentType(type);
        document.setFile(file());
        document.setVersionNumber(version);
        document.setCurrent(false);
        entityManager.persist(document);
    }

    private DocumentType type() {
        DocumentType type = new DocumentType();
        type.setCode("DOC_" + SEQUENCE.incrementAndGet());
        type.setName("Document type");
        type.setScope(DocumentTypeScope.BOTH);
        entityManager.persist(type);
        return type;
    }

    private StoredFile file() {
        StoredFile file = new StoredFile();
        file.setStorageKey("document-test-" + SEQUENCE.incrementAndGet());
        file.setOriginalName("document.pdf");
        file.setMimeType("application/pdf");
        file.setSizeBytes(100);
        file.setChecksumSha256("a".repeat(64));
        entityManager.persist(file);
        return file;
    }

    private AddressDtos.Request address() {
        return new AddressDtos.Request("83200000", "Test Street", "10", null, "Centro", "Paranaguá", "PR", "BR");
    }
}
