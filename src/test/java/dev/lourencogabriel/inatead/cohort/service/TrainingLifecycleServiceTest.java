package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingLifecycleServiceTest {

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private CohortRepository cohortRepository;
    @Mock
    private CohortEnrollmentRepository enrollmentRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private CohortEnrollmentLifecycleService enrollmentLifecycleService;
    @Mock
    private LifecycleStatusHistoryService historyService;

    @Test
    void shouldAutomaticallyEndElapsedContractsAndTheirEnrollments() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        Instant changedAt = Instant.parse("2026-06-15T12:00:00Z");
        Contract contract = new Contract();
        contract.setId("contract");
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setEndDate(today.minusDays(1));
        when(contractRepository.findEndingCandidateIds(any(), any()))
                .thenReturn(List.of("contract"));
        when(contractRepository.findByIdForUpdate("contract"))
                .thenReturn(Optional.of(contract));
        when(cohortRepository.findCompletionCandidateIds(any(), any())).thenReturn(List.of());
        when(enrollmentRepository.findCompletionCandidateIds(any(), any())).thenReturn(List.of());

        var service = new TrainingLifecycleService(
                contractRepository,
                cohortRepository,
                enrollmentRepository,
                lessonRepository,
                enrollmentLifecycleService,
                historyService
        );
        var result = service.updateStatuses(today, changedAt);

        assertEquals(1, result.endedContracts());
        assertEquals(ContractStatus.ENDED, contract.getStatus());
        verify(enrollmentLifecycleService).completeForContract(
                "contract",
                today.minusDays(1),
                changedAt,
                null
        );
    }
}
