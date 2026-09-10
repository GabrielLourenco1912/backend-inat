package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.dto.CohortEnrollmentDtos;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortEnrollmentServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo")
    );

    @Mock
    private CohortEnrollmentRepository enrollmentRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private CohortRepository cohortRepository;
    @Mock
    private LessonParticipantRepository participantRepository;
    @Mock
    private CohortEnrollmentLifecycleService lifecycleService;
    @Mock
    private LifecycleStatusHistoryService historyService;

    private CohortEnrollmentService service;
    private Contract contract;
    private Cohort cohort;

    @BeforeEach
    void setUp() {
        service = new CohortEnrollmentService(
                enrollmentRepository,
                contractRepository,
                cohortRepository,
                participantRepository,
                lifecycleService,
                historyService,
                CLOCK
        );
        contract = contract();
        cohort = cohort();
        lenient().when(contractRepository.findByIdForUpdate("contract"))
                .thenReturn(Optional.of(contract));
        lenient().when(cohortRepository.findByIdForUpdate("cohort"))
                .thenReturn(Optional.of(cohort));
        lenient().when(historyService.find(any(), any())).thenReturn(List.of());
    }

    @Test
    void shouldRejectEnrollmentOutsideCohortPeriod() {
        CohortEnrollmentDtos.Request request = request(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 10, 31),
                EnrollmentStatus.PENDING,
                null
        );

        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }

    @Test
    void shouldOnlyCreatePendingEnrollments() {
        CohortEnrollmentDtos.Request request = request(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 10, 31),
                EnrollmentStatus.ACTIVE,
                null
        );

        assertThrows(BusinessRuleException.class, () -> service.create(request));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectEnrollmentWhenCohortCapacityIsReached() {
        cohort.setMaxLearners(1);
        when(enrollmentRepository.countByCohort_IdAndStatusIn(any(), any())).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> service.create(request(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 10, 31),
                EnrollmentStatus.PENDING,
                null
        )));
    }

    @Test
    void shouldCreatePendingEnrollmentWithinBothParentPeriods() {
        when(enrollmentRepository.save(any())).thenAnswer(invocation -> {
            var enrollment = invocation.getArgument(0, dev.lourencogabriel.inatead.cohort.model.CohortEnrollment.class);
            enrollment.setId("enrollment");
            return enrollment;
        });

        var response = service.create(request(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 10, 31),
                EnrollmentStatus.PENDING,
                null
        ));

        assertEquals(EnrollmentStatus.PENDING, response.status());
    }

    private CohortEnrollmentDtos.Request request(
            LocalDate start,
            LocalDate end,
            EnrollmentStatus status,
            String reason
    ) {
        return new CohortEnrollmentDtos.Request(
                "contract",
                "cohort",
                start,
                end,
                status,
                reason
        );
    }

    private Contract contract() {
        Person person = new Person();
        person.setId("person");
        person.setStatus(RecordStatus.ACTIVE);
        Learner learner = new Learner();
        learner.setId("learner");
        learner.setPerson(person);
        learner.setStatus(RecordStatus.ACTIVE);
        Contract value = new Contract();
        value.setId("contract");
        value.setLearner(learner);
        value.setStartDate(LocalDate.of(2026, 1, 1));
        value.setEndDate(LocalDate.of(2026, 12, 31));
        value.setStatus(ContractStatus.ACTIVE);
        return value;
    }

    private Cohort cohort() {
        Cohort value = new Cohort();
        value.setId("cohort");
        value.setStartDate(LocalDate.of(2026, 3, 1));
        value.setEndDate(LocalDate.of(2026, 11, 30));
        value.setStatus(CohortStatus.ACTIVE);
        return value;
    }
}
