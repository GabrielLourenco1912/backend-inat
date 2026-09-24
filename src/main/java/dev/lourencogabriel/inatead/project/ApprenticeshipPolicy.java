package dev.lourencogabriel.inatead.project;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

/** INAT-specific rules; contract storage and the general workload API remain in minutes. */
@Component
@RequiredArgsConstructor
public class ApprenticeshipPolicy {

    public static final int ONLINE_WEEKLY_WORKLOAD_MINUTES = 30 * 60;
    private static final Set<LessonStatus> OPEN_LESSONS = Set.of(LessonStatus.SCHEDULED, LessonStatus.IN_PROGRESS);
    private final ContractRepository contractRepository;
    private final LessonParticipantRepository participantRepository;
    private final Clock clock;

    public static boolean allowsDeliveryMode(DeliveryMode deliveryMode, Contract contract) {
        return deliveryMode != DeliveryMode.ONLINE
                || contract != null && contract.getWeeklyWorkloadMinutes() == ONLINE_WEEKLY_WORKLOAD_MINUTES;
    }

    public void validateParticipation(LessonParticipant participant, Lesson lesson, LocalDate lessonDate) {
        if (lesson.getDeliveryMode() != DeliveryMode.ONLINE
                || participant.getStatus() == ParticipationStatus.CANCELLED) {
            return;
        }
        var source = participant.getSourceEnrollment();
        boolean eligible = source != null
                ? isEligibleContract(source.getContract(), lessonDate)
                : contractRepository.findAllByLearner_IdOrderByStartDateDesc(participant.getLearner().getId()).stream()
                        .anyMatch(contract -> isEligibleContract(contract, lessonDate));
        if (!eligible) {
            throw new BusinessRuleException(
                    "Online lessons require an active 30-hour weekly contract (1800 minutes) on the lesson date"
            );
        }
    }

    public void validateWorkloadChange(Contract contract, int requestedMinutes) {
        if (requestedMinutes == contract.getWeeklyWorkloadMinutes()
                || requestedMinutes == ONLINE_WEEKLY_WORKLOAD_MINUTES) {
            return;
        }
        boolean assignedOnline = participantRepository
                .findAllByLearner_IdAndStatusAndLesson_DeliveryModeAndLesson_StatusIn(
                        contract.getLearner().getId(), ParticipationStatus.EXPECTED, DeliveryMode.ONLINE, OPEN_LESSONS
                ).stream().anyMatch(participant -> {
                    var source = participant.getSourceEnrollment();
                    return source != null
                            ? source.getContract().getId().equals(contract.getId())
                            : coversDate(contract, BusinessTime.date(participant.getLesson().getStartsAt()));
                });
        if (assignedOnline) {
            throw new BusinessRuleException(
                    "The weekly workload cannot change from 30 hours while the contract has participants in open online lessons"
            );
        }
    }

    private boolean isEligibleContract(Contract contract, LocalDate date) {
        return allowsDeliveryMode(DeliveryMode.ONLINE, contract)
                && contract.getStatus() == ContractStatus.ACTIVE
                && coversDate(contract, date);
    }

    private boolean coversDate(Contract contract, LocalDate date) {
        return contract.getStartDate() != null && !contract.getStartDate().isAfter(date)
                && (contract.getEndDate() == null || !contract.getEndDate().isBefore(date));
    }
}
