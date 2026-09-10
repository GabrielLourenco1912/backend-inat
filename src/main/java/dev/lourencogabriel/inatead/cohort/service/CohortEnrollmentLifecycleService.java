package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.LifecycleAggregateType;
import dev.lourencogabriel.inatead.shared.model.LifecycleChangeReason;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CohortEnrollmentLifecycleService {

    private static final Set<EnrollmentStatus> OPEN_STATUSES = Set.of(
            EnrollmentStatus.PENDING,
            EnrollmentStatus.ACTIVE
    );
    private static final Set<LessonStatus> OPEN_LESSON_STATUSES = Set.of(
            LessonStatus.SCHEDULED,
            LessonStatus.IN_PROGRESS
    );

    private final CohortEnrollmentRepository enrollmentRepository;
    private final LessonParticipantRepository participantRepository;
    private final LifecycleStatusHistoryService historyService;

    public int cancelForContract(
            String contractId,
            LocalDate today,
            Instant changedAt,
            String actorUserId,
            String detail
    ) {
        return enrollmentRepository.findAllByContract_IdAndStatusIn(contractId, OPEN_STATUSES)
                .stream()
                .mapToInt(enrollment -> cancel(
                        enrollment,
                        today,
                        changedAt,
                        actorUserId,
                        LifecycleChangeReason.PARENT_CANCELLED,
                        detail
                ))
                .sum();
    }

    public int cancelForCohort(
            String cohortId,
            LocalDate today,
            Instant changedAt,
            String actorUserId,
            String detail
    ) {
        return enrollmentRepository.findAllByCohort_IdAndStatusIn(cohortId, OPEN_STATUSES)
                .stream()
                .mapToInt(enrollment -> cancel(
                        enrollment,
                        today,
                        changedAt,
                        actorUserId,
                        LifecycleChangeReason.PARENT_CANCELLED,
                        detail
                ))
                .sum();
    }

    public int completeForContract(
            String contractId,
            LocalDate effectiveEnd,
            Instant changedAt,
            String actorUserId
    ) {
        return completeOpenEnrollments(
                enrollmentRepository.findAllByContract_IdAndStatusIn(contractId, OPEN_STATUSES),
                effectiveEnd,
                changedAt,
                actorUserId,
                "Contract ended"
        );
    }

    public int completeForCohort(
            String cohortId,
            LocalDate effectiveEnd,
            Instant changedAt,
            String actorUserId
    ) {
        return completeOpenEnrollments(
                enrollmentRepository.findAllByCohort_IdAndStatusIn(cohortId, OPEN_STATUSES),
                effectiveEnd,
                changedAt,
                actorUserId,
                "Cohort completed"
        );
    }

    public void cancelDirectly(
            CohortEnrollment enrollment,
            LocalDate today,
            Instant changedAt,
            String actorUserId,
            String detail
    ) {
        cancel(
                enrollment,
                today,
                changedAt,
                actorUserId,
                LifecycleChangeReason.ADMIN_TRANSITION,
                detail
        );
    }

    public boolean completeDirectly(CohortEnrollment enrollment, Instant changedAt) {
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            return false;
        }
        EnrollmentStatus previousStatus = enrollment.getStatus();
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        enrollmentRepository.save(enrollment);
        historyService.append(
                LifecycleAggregateType.COHORT_ENROLLMENT,
                enrollment.getId(),
                previousStatus,
                EnrollmentStatus.COMPLETED,
                LifecycleChangeReason.AUTOMATIC_TRANSITION,
                "Enrollment period elapsed",
                null,
                changedAt
        );
        return true;
    }

    private int cancel(
            CohortEnrollment enrollment,
            LocalDate today,
            Instant changedAt,
            String actorUserId,
            LifecycleChangeReason reason,
            String detail
    ) {
        EnrollmentStatus previousStatus = enrollment.getStatus();
        if (!OPEN_STATUSES.contains(previousStatus)) {
            return 0;
        }
        if (!enrollment.getStartDate().isAfter(today)
                && (enrollment.getEndDate() == null || enrollment.getEndDate().isAfter(today))) {
            enrollment.setEndDate(today);
        }
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollmentRepository.save(enrollment);
        cancelOpenParticipants(enrollment);
        historyService.append(
                LifecycleAggregateType.COHORT_ENROLLMENT,
                enrollment.getId(),
                previousStatus,
                EnrollmentStatus.CANCELLED,
                reason,
                detail,
                actorUserId,
                changedAt
        );
        return 1;
    }

    private int completeOpenEnrollments(
            java.util.List<CohortEnrollment> enrollments,
            LocalDate effectiveEnd,
            Instant changedAt,
            String actorUserId,
            String detail
    ) {
        int changed = 0;
        for (CohortEnrollment enrollment : enrollments) {
            EnrollmentStatus previousStatus = enrollment.getStatus();
            EnrollmentStatus targetStatus = previousStatus == EnrollmentStatus.ACTIVE
                    ? EnrollmentStatus.COMPLETED
                    : EnrollmentStatus.CANCELLED;
            if (!enrollment.getStartDate().isAfter(effectiveEnd)
                    && (enrollment.getEndDate() == null
                    || enrollment.getEndDate().isAfter(effectiveEnd))) {
                enrollment.setEndDate(effectiveEnd);
            }
            enrollment.setStatus(targetStatus);
            enrollmentRepository.save(enrollment);
            if (targetStatus == EnrollmentStatus.CANCELLED) {
                cancelOpenParticipants(enrollment);
            }
            historyService.append(
                    LifecycleAggregateType.COHORT_ENROLLMENT,
                    enrollment.getId(),
                    previousStatus,
                    targetStatus,
                    LifecycleChangeReason.PARENT_COMPLETED,
                    detail,
                    actorUserId,
                    changedAt
            );
            changed++;
        }
        return changed;
    }

    private void cancelOpenParticipants(CohortEnrollment enrollment) {
        var participants = participantRepository
                .findAllBySourceEnrollment_IdAndStatusNotAndLesson_StatusIn(
                        enrollment.getId(),
                        ParticipationStatus.CANCELLED,
                        OPEN_LESSON_STATUSES
                );
        participants.forEach(participant -> participant.setStatus(ParticipationStatus.CANCELLED));
        participantRepository.saveAll(participants);
    }
}
