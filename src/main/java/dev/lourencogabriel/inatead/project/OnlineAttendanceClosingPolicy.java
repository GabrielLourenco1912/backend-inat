package dev.lourencogabriel.inatead.project;

import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static dev.lourencogabriel.inatead.shared.time.BusinessTime.ZONE;

/** INAT-specific attendance cut-off derived from the participant's employer. */
@Component
@RequiredArgsConstructor
public class OnlineAttendanceClosingPolicy {

    private final ContractRepository contractRepository;

    public Optional<Instant> submissionCutoffFor(LessonParticipant participant) {
        Contract contract = participant.getSourceEnrollment() == null
                ? findContractForLesson(participant).orElse(null)
                : participant.getSourceEnrollment().getContract();
        if (contract == null || contract.getEmployer() == null
                || contract.getEmployer().getAttendanceClosingDay() == null) {
            return Optional.empty();
        }

        LocalDate lessonDate = participant.getLesson().getStartsAt()
                .atZone(ZONE)
                .toLocalDate();
        int closingDay = contract.getEmployer().getAttendanceClosingDay();
        YearMonth closingMonth = YearMonth.from(lessonDate);
        LocalDate closingDate = closingDate(closingMonth, closingDay);
        if (lessonDate.isAfter(closingDate)) {
            closingDate = closingDate(closingMonth.plusMonths(1), closingDay);
        }
        return Optional.of(closingDate.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    private Optional<Contract> findContractForLesson(LessonParticipant participant) {
        LocalDate lessonDate = participant.getLesson().getStartsAt()
                .atZone(ZONE)
                .toLocalDate();
        return contractRepository
                .findAllByLearner_IdOrderByStartDateDesc(participant.getLearner().getId())
                .stream()
                .filter(contract -> ApprenticeshipPolicy.allowsDeliveryMode(
                        participant.getLesson().getDeliveryMode(),
                        contract
                ))
                .filter(contract -> !contract.getStartDate().isAfter(lessonDate))
                .filter(contract -> contract.getEndDate() == null
                        || !contract.getEndDate().isBefore(lessonDate))
                .findFirst();
    }

    private LocalDate closingDate(YearMonth month, int closingDay) {
        return month.atDay(Math.min(closingDay, month.lengthOfMonth()));
    }
}
