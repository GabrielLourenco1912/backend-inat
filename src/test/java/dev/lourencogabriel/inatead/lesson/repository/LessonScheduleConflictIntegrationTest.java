package dev.lourencogabriel.inatead.lesson.repository;

import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LessonScheduleConflictIntegrationTest {

    private static final Instant FIRST_START = Instant.parse("2026-09-10T10:00:00Z");
    private static final Instant FIRST_END = Instant.parse("2026-09-10T11:00:00Z");
    private static final Instant SECOND_START = FIRST_END;
    private static final Instant SECOND_END = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant OVERLAPPING_START = Instant.parse("2026-09-10T10:30:00Z");
    private static final Instant OVERLAPPING_END = Instant.parse("2026-09-10T11:30:00Z");

    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private LearnerRepository learnerRepository;
    @Autowired
    private CohortRepository cohortRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private LessonParticipantRepository participantRepository;

    private Person instructor;
    private Learner learner;
    private Lesson firstLesson;
    private Lesson secondLesson;
    private LessonParticipant firstParticipant;

    @BeforeEach
    void setUp() {
        instructor = personRepository.save(person());

        learner = new Learner();
        learner.setPerson(instructor);
        learner = learnerRepository.save(learner);

        Cohort firstCohort = cohortRepository.save(cohort("SCHEDULE-A"));
        Cohort secondCohort = cohortRepository.save(cohort("SCHEDULE-B"));
        firstLesson = lessonRepository.save(lesson(
                firstCohort,
                "First lesson",
                FIRST_START,
                FIRST_END
        ));
        secondLesson = lessonRepository.save(lesson(
                secondCohort,
                "Second lesson",
                SECOND_START,
                SECOND_END
        ));

        firstParticipant = new LessonParticipant();
        firstParticipant.setLesson(firstLesson);
        firstParticipant.setLearner(learner);
        firstParticipant.setStatus(ParticipationStatus.EXPECTED);
        firstParticipant = participantRepository.save(firstParticipant);
    }

    @Test
    void shouldDetectLearnerOverlapAndAllowAdjacentLessons() {
        assertTrue(participantRepository.existsLearnerScheduleConflict(
                learner.getId(),
                secondLesson.getId(),
                OVERLAPPING_START,
                OVERLAPPING_END,
                ParticipationStatus.CANCELLED,
                LessonStatus.CANCELLED
        ));
        assertFalse(participantRepository.existsLearnerScheduleConflict(
                learner.getId(),
                secondLesson.getId(),
                SECOND_START,
                SECOND_END,
                ParticipationStatus.CANCELLED,
                LessonStatus.CANCELLED
        ));
    }

    @Test
    void shouldDetectConflictWhenChangingLessonWithListedLearners() {
        LessonParticipant secondParticipant = new LessonParticipant();
        secondParticipant.setLesson(secondLesson);
        secondParticipant.setLearner(learner);
        secondParticipant.setStatus(ParticipationStatus.EXPECTED);
        participantRepository.save(secondParticipant);

        assertTrue(participantRepository.existsScheduleConflictForLessonParticipants(
                secondLesson.getId(),
                OVERLAPPING_START,
                OVERLAPPING_END,
                ParticipationStatus.CANCELLED,
                LessonStatus.CANCELLED
        ));
        assertFalse(participantRepository.existsScheduleConflictForLessonParticipants(
                secondLesson.getId(),
                SECOND_START,
                SECOND_END,
                ParticipationStatus.CANCELLED,
                LessonStatus.CANCELLED
        ));
    }

    @Test
    void shouldIgnoreCancelledParticipationAndDetectInstructorOverlap() {
        assertTrue(lessonRepository.existsInstructorScheduleConflict(
                instructor.getId(),
                secondLesson.getId(),
                OVERLAPPING_START,
                OVERLAPPING_END,
                LessonStatus.CANCELLED
        ));
        assertFalse(lessonRepository.existsInstructorScheduleConflict(
                instructor.getId(),
                secondLesson.getId(),
                SECOND_START,
                SECOND_END,
                LessonStatus.CANCELLED
        ));

        firstParticipant.setStatus(ParticipationStatus.CANCELLED);
        participantRepository.saveAndFlush(firstParticipant);
        assertFalse(participantRepository.existsLearnerScheduleConflict(
                learner.getId(),
                secondLesson.getId(),
                OVERLAPPING_START,
                OVERLAPPING_END,
                ParticipationStatus.CANCELLED,
                LessonStatus.CANCELLED
        ));
    }

    private Person person() {
        Address address = new Address();
        address.setPostalCode("80000000");
        address.setStreet("Schedule Test Street");
        address.setStreetNumber("100");
        address.setDistrict("Center");
        address.setCity("Curitiba");
        address.setStateCode("PR");
        address.setCountryCode("BR");

        Person person = new Person();
        person.setAddress(address);
        person.setFullName("Schedule test person");
        person.setTaxId("90000000001");
        person.setPhoneNumber("41999999999");
        person.setBirthDate(LocalDate.of(2000, 1, 1));
        return person;
    }

    private Cohort cohort(String code) {
        Cohort cohort = new Cohort();
        cohort.setCode(code);
        cohort.setName(code);
        cohort.setDefaultWeekday(4);
        cohort.setShiftCode("MORNING");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));
        cohort.setStatus(CohortStatus.ACTIVE);
        return cohort;
    }

    private Lesson lesson(
            Cohort cohort,
            String title,
            Instant startsAt,
            Instant endsAt
    ) {
        Lesson lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructor);
        lesson.setTitle(title);
        lesson.setStartsAt(startsAt);
        lesson.setEndsAt(endsAt);
        lesson.setDeliveryMode(DeliveryMode.ONSITE);
        lesson.setRoom("Room 1");
        lesson.setStatus(LessonStatus.SCHEDULED);
        return lesson;
    }
}
