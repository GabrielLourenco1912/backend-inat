package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.LifecycleAggregateType;
import dev.lourencogabriel.inatead.shared.model.LifecycleChangeReason;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TrainingLifecycleService {

    private static final Set<ContractStatus> ENDING_CONTRACT_STATUSES = Set.of(
            ContractStatus.ACTIVE,
            ContractStatus.SUSPENDED
    );
    private static final Set<LessonStatus> OPEN_LESSON_STATUSES = Set.of(
            LessonStatus.SCHEDULED,
            LessonStatus.IN_PROGRESS
    );

    private final ContractRepository contractRepository;
    private final CohortRepository cohortRepository;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final CohortEnrollmentLifecycleService enrollmentLifecycleService;
    private final LifecycleStatusHistoryService historyService;

    @Transactional
    public UpdateSummary updateStatuses(LocalDate today, Instant changedAt) {
        int endedContracts = endElapsedContracts(today, changedAt);
        int completedCohorts = completeElapsedCohorts(today, changedAt);
        int completedEnrollments = completeElapsedEnrollments(today, changedAt);
        return new UpdateSummary(endedContracts, completedCohorts, completedEnrollments);
    }

    private int endElapsedContracts(LocalDate today, Instant changedAt) {
        int changed = 0;
        for (String id : contractRepository.findEndingCandidateIds(
                ENDING_CONTRACT_STATUSES,
                today
        )) {
            var contract = contractRepository.findByIdForUpdate(id).orElse(null);
            if (contract == null
                    || !ENDING_CONTRACT_STATUSES.contains(contract.getStatus())
                    || contract.getEndDate() == null
                    || !contract.getEndDate().isBefore(today)) {
                continue;
            }
            ContractStatus previous = contract.getStatus();
            contract.setStatus(ContractStatus.ENDED);
            contractRepository.save(contract);
            historyService.append(
                    LifecycleAggregateType.CONTRACT,
                    contract.getId(),
                    previous,
                    ContractStatus.ENDED,
                    LifecycleChangeReason.AUTOMATIC_TRANSITION,
                    "Contract period elapsed",
                    null,
                    changedAt
            );
            enrollmentLifecycleService.completeForContract(
                    contract.getId(),
                    contract.getEndDate(),
                    changedAt,
                    null
            );
            changed++;
        }
        return changed;
    }

    private int completeElapsedCohorts(LocalDate today, Instant changedAt) {
        int changed = 0;
        for (String id : cohortRepository.findCompletionCandidateIds(
                Set.of(CohortStatus.ACTIVE),
                today
        )) {
            var cohort = cohortRepository.findByIdForUpdate(id).orElse(null);
            if (cohort == null
                    || cohort.getStatus() != CohortStatus.ACTIVE
                    || cohort.getEndDate() == null
                    || !cohort.getEndDate().isBefore(today)
                    || lessonRepository.existsByCohort_IdAndStatusIn(
                            cohort.getId(),
                            OPEN_LESSON_STATUSES
                    )) {
                continue;
            }
            cohort.setStatus(CohortStatus.COMPLETED);
            cohortRepository.save(cohort);
            historyService.append(
                    LifecycleAggregateType.COHORT,
                    cohort.getId(),
                    CohortStatus.ACTIVE,
                    CohortStatus.COMPLETED,
                    LifecycleChangeReason.AUTOMATIC_TRANSITION,
                    "Cohort period elapsed",
                    null,
                    changedAt
            );
            enrollmentLifecycleService.completeForCohort(
                    cohort.getId(),
                    cohort.getEndDate(),
                    changedAt,
                    null
            );
            changed++;
        }
        return changed;
    }

    private int completeElapsedEnrollments(LocalDate today, Instant changedAt) {
        int changed = 0;
        for (String id : enrollmentRepository.findCompletionCandidateIds(
                EnrollmentStatus.ACTIVE,
                today
        )) {
            var enrollment = enrollmentRepository.findByIdForUpdate(id).orElse(null);
            if (enrollment != null
                    && enrollment.getEndDate() != null
                    && enrollment.getEndDate().isBefore(today)
                    && enrollmentLifecycleService.completeDirectly(enrollment, changedAt)) {
                changed++;
            }
        }
        return changed;
    }

    public record UpdateSummary(
            int endedContracts,
            int completedCohorts,
            int completedEnrollments
    ) {
        public boolean hasChanges() {
            return endedContracts > 0 || completedCohorts > 0 || completedEnrollments > 0;
        }
    }
}
