package dev.lourencogabriel.inatead.contract.service;

import dev.lourencogabriel.inatead.project.ApprenticeshipPolicy;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.service.CohortEnrollmentLifecycleService;
import dev.lourencogabriel.inatead.contract.dto.ContractDtos;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractDocumentRepository;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo")
    );

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private LearnerRepository learnerRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private CohortEnrollmentRepository enrollmentRepository;
    @Mock
    private ContractDocumentRepository documentRepository;
    @Mock
    private CohortEnrollmentLifecycleService enrollmentLifecycleService;
    @Mock
    private LifecycleStatusHistoryService historyService;
    @Mock
    private ApprenticeshipPolicy apprenticeshipPolicy;

    private ContractService service;
    private Learner learner;
    private Organization employer;

    @BeforeEach
    void setUp() {
        service = new ContractService(
                contractRepository,
                learnerRepository,
                organizationRepository,
                enrollmentRepository,
                documentRepository,
                enrollmentLifecycleService,
                historyService,
                CLOCK,
                apprenticeshipPolicy
        );
        learner = learner(LocalDate.of(2000, 1, 1), false);
        employer = organization("employer", OrganizationType.EMPLOYER);
        lenient().when(learnerRepository.findByIdForUpdate("learner"))
                .thenReturn(Optional.of(learner));
        lenient().when(organizationRepository.findById("employer"))
                .thenReturn(Optional.of(employer));
        lenient().when(historyService.find(any(), any())).thenReturn(List.of());
    }

    @Test
    void shouldCreateDraftWithoutRequiringAContractDocument() {
        stubSave();

        ContractDtos.Response response = assertDoesNotThrow(() -> service.create(request(
                null,
                LocalDate.of(2026, 12, 31),
                ContractStatus.DRAFT,
                null
        )));

        assertNull(response.schoolId());
        assertEquals(ContractStatus.DRAFT, response.status());
        verifyNoInteractions(documentRepository);
    }

    @Test
    void shouldRejectNonDraftCreation() {
        assertThrows(BusinessRuleException.class, () -> service.create(request(
                null,
                LocalDate.of(2026, 12, 31),
                ContractStatus.ACTIVE,
                null
        )));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void shouldRequireSchoolForMinorWhoHasNotCompletedHighSchool() {
        learner.getPerson().setBirthDate(LocalDate.of(2010, 1, 1));
        learner.setHasCompletedHighSchool(false);

        assertThrows(BusinessRuleException.class, () -> service.create(request(
                null,
                LocalDate.of(2026, 12, 31),
                ContractStatus.DRAFT,
                null
        )));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void shouldAllowExtendingTheEndDate() {
        Contract contract = existingContract(ContractStatus.ACTIVE, LocalDate.of(2026, 12, 31));
        stubUpdate(contract);

        ContractDtos.Response response = service.update(
                contract.getId(),
                request(null, LocalDate.of(2027, 6, 30), ContractStatus.ACTIVE, null),
                "admin"
        );

        assertEquals(LocalDate.of(2027, 6, 30), response.endDate());
    }

    @Test
    void shouldRejectShorteningTheEndDateEvenWhenTheNewDateIsInTheFuture() {
        Contract contract = existingContract(ContractStatus.ACTIVE, LocalDate.of(2027, 12, 31));
        stubUpdateLookup(contract);

        assertThrows(BusinessRuleException.class, () -> service.update(
                contract.getId(),
                request(null, LocalDate.of(2027, 6, 30), ContractStatus.ACTIVE, null),
                "admin"
        ));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void shouldOnlyMoveTheEndDateToTodayThroughCancellation() {
        Contract contract = existingContract(ContractStatus.ACTIVE, LocalDate.of(2027, 12, 31));
        stubUpdate(contract);

        ContractDtos.Response response = service.update(
                contract.getId(),
                request(null, contract.getEndDate(), ContractStatus.CANCELLED, "Administrative cancellation"),
                "admin"
        );

        assertEquals(LocalDate.now(CLOCK), response.endDate());
        assertEquals(ContractStatus.CANCELLED, response.status());
        verify(enrollmentLifecycleService).cancelForContract(
                contract.getId(),
                LocalDate.now(CLOCK),
                CLOCK.instant(),
                "admin",
                "Contract cancelled: Administrative cancellation"
        );
    }

    @Test
    void shouldRejectChangingTheEndDateToTodayWithoutCancellation() {
        Contract contract = existingContract(ContractStatus.ACTIVE, LocalDate.of(2027, 12, 31));
        stubUpdateLookup(contract);

        assertThrows(BusinessRuleException.class, () -> service.update(
                contract.getId(),
                request(null, LocalDate.now(CLOCK), ContractStatus.ACTIVE, null),
                "admin"
        ));
    }

    @Test
    void shouldValidateWorkloadBeforeChangingAnOnlineParticipantsContract() {
        Contract contract = existingContract(ContractStatus.ACTIVE, LocalDate.of(2026, 12, 31));
        stubUpdateLookup(contract);
        doThrow(new BusinessRuleException("Contract has open online participants"))
                .when(apprenticeshipPolicy).validateWorkloadChange(contract, 1200);
        var request = new ContractDtos.Request("learner", "employer", null, contract.getStartDate(),
                contract.getEndDate(), contract.getMonthlySalary(), 1200, ContractStatus.ACTIVE, null);

        assertThrows(BusinessRuleException.class, () -> service.update(contract.getId(), request, "admin"));

        assertEquals(1800, contract.getWeeklyWorkloadMinutes());
        verify(contractRepository, never()).save(any());
    }

    @Test
    void shouldKeepAcceptingMinutesInTheGeneralContractApi() {
        stubSave();
        var request = new ContractDtos.Request("learner", "employer", null, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), new BigDecimal("1200.00"), 1500, ContractStatus.DRAFT, null);

        assertEquals(1500, service.create(request).weeklyWorkloadMinutes());
    }

    private void stubSave() {
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> {
            Contract value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId("contract");
            }
            return value;
        });
    }

    private void stubUpdate(Contract contract) {
        stubUpdateLookup(contract);
        lenient().when(contractRepository.existsOverlappingContract(
                any(),
                anyCollection(),
                any(),
                any(),
                any()
        )).thenReturn(false);
        lenient().when(enrollmentRepository.findAllByContract_Id(contract.getId()))
                .thenReturn(List.of());
        stubSave();
    }

    private void stubUpdateLookup(Contract contract) {
        when(contractRepository.findByIdForUpdate(contract.getId())).thenReturn(Optional.of(contract));
    }

    private Contract existingContract(ContractStatus status, LocalDate endDate) {
        Contract contract = new Contract();
        contract.setId("contract");
        contract.setLearner(learner);
        contract.setEmployer(employer);
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setEndDate(endDate);
        contract.setMonthlySalary(new BigDecimal("1200.00"));
        contract.setWeeklyWorkloadMinutes(1800);
        contract.setStatus(status);
        return contract;
    }

    private ContractDtos.Request request(
            String schoolId,
            LocalDate endDate,
            ContractStatus status,
            String statusReason
    ) {
        return new ContractDtos.Request(
                "learner",
                "employer",
                schoolId,
                LocalDate.of(2026, 1, 1),
                endDate,
                new BigDecimal("1200.00"),
                1800,
                status,
                statusReason
        );
    }

    private Learner learner(LocalDate birthDate, boolean completedHighSchool) {
        Person person = new Person();
        person.setId("person");
        person.setBirthDate(birthDate);
        person.setStatus(RecordStatus.ACTIVE);
        Learner value = new Learner();
        value.setId("learner");
        value.setPerson(person);
        value.setHasCompletedHighSchool(completedHighSchool);
        value.setStatus(RecordStatus.ACTIVE);
        return value;
    }

    private Organization organization(String id, OrganizationType type) {
        Organization value = new Organization();
        value.setId(id);
        value.setOrganizationType(type);
        value.setStatus(RecordStatus.ACTIVE);
        return value;
    }
}
