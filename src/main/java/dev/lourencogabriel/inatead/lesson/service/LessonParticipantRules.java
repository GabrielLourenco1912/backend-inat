package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;

import java.time.LocalDate;

final class LessonParticipantRules {

    private LessonParticipantRules() {
    }

    static void validateContext(
            LessonParticipant participant,
            Lesson hostLesson,
            LocalDate lessonDate
    ) {
        if (participant.getLearner().getStatus() != RecordStatus.ACTIVE
                || participant.getLearner().getPerson().getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("Lesson participation requires an active learner and person");
        }
        if (hostLesson.getCohort().getStatus() != CohortStatus.ACTIVE) {
            throw new BusinessRuleException("Lesson participation requires an active cohort");
        }
        CohortEnrollment source = participant.getSourceEnrollment();
        if (source != null
                && !source.getContract().getLearner().getId()
                .equals(participant.getLearner().getId())) {
            throw new BusinessRuleException("The source enrollment belongs to another learner");
        }

        ParticipationType type = participant.getParticipationType();
        if ((type == ParticipationType.REGULAR || type == ParticipationType.TRANSFERRED)
                && source == null) {
            throw new BusinessRuleException(type + " participation requires a source enrollment");
        }

        if (source != null) {
            boolean enrollmentCoversLesson = source.getStatus() == EnrollmentStatus.ACTIVE
                    && !source.getStartDate().isAfter(lessonDate)
                    && (source.getEndDate() == null || !source.getEndDate().isBefore(lessonDate));
            if (!enrollmentCoversLesson) {
                throw new BusinessRuleException("The source enrollment is not active on the lesson date");
            }
            if (source.getContract().getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessRuleException("The source enrollment requires an active contract");
            }

            boolean sameCohort = source.getCohort().getId()
                    .equals(hostLesson.getCohort().getId());
            if (type == ParticipationType.REGULAR && !sameCohort) {
                throw new BusinessRuleException("A regular participant must belong to the host cohort");
            }
            if (type == ParticipationType.TRANSFERRED && sameCohort) {
                throw new BusinessRuleException("A transferred participant must come from another cohort");
            }
        }

        if (type != ParticipationType.REGULAR && participant.getAssignmentReason() == null) {
            throw new BusinessRuleException("Non-regular participation requires a reason");
        }
    }
}
