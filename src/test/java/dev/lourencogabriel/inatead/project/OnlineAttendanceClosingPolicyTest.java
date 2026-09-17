package dev.lourencogabriel.inatead.project;

import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class OnlineAttendanceClosingPolicyTest {

    @Mock
    private ContractRepository contractRepository;

    @Test
    void shouldKeepCurrentBehaviorWhenTheEmployerHasNoClosingDay() {
        var policy = new OnlineAttendanceClosingPolicy(contractRepository);

        assertTrue(policy.submissionCutoffFor(participant("2026-09-10T13:00:00Z", null)).isEmpty());
    }

    @Test
    void shouldUseTheCurrentMonthClosingForALessonOnTheClosingDay() {
        var policy = new OnlineAttendanceClosingPolicy(contractRepository);

        assertEquals(
                Instant.parse("2026-09-21T03:00:00Z"),
                policy.submissionCutoffFor(participant("2026-09-20T13:00:00Z", 20)).orElseThrow()
        );
    }

    @Test
    void shouldUseTheNextMonthClosingForALessonAfterTheClosingDay() {
        var policy = new OnlineAttendanceClosingPolicy(contractRepository);

        assertEquals(
                Instant.parse("2026-10-21T03:00:00Z"),
                policy.submissionCutoffFor(participant("2026-09-21T13:00:00Z", 20)).orElseThrow()
        );
    }

    @Test
    void shouldUseTheLastDayWhenTheConfiguredDayDoesNotExistInTheMonth() {
        var policy = new OnlineAttendanceClosingPolicy(contractRepository);

        assertEquals(
                Instant.parse("2026-03-01T03:00:00Z"),
                policy.submissionCutoffFor(participant("2026-02-10T13:00:00Z", 31)).orElseThrow()
        );
    }

    private LessonParticipant participant(String startsAt, Integer closingDay) {
        Organization employer = new Organization();
        employer.setAttendanceClosingDay(closingDay);

        Learner learner = new Learner();
        learner.setId("learner-1");

        Contract contract = new Contract();
        contract.setEmployer(employer);
        contract.setLearner(learner);

        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setContract(contract);

        Lesson lesson = new Lesson();
        lesson.setStartsAt(Instant.parse(startsAt));
        lesson.setDeliveryMode(DeliveryMode.ONLINE);

        LessonParticipant participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setSourceEnrollment(enrollment);
        return participant;
    }
}
