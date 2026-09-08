package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AcademicLifecycleIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Autowired
    private AcademicLifecycleService service;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CohortRepository cohortRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private ActivityRepository activityRepository;

    @Test
    void shouldPersistOnlyApplicableTimeBasedTransitions() {
        Person instructor = personRepository.save(person());
        User creator = new User("Lifecycle creator", "lifecycle@example.com", "hash");
        creator.setPerson(instructor);
        creator = userRepository.save(creator);
        Cohort cohort = cohortRepository.save(cohort());

        Lesson elapsed = lessonRepository.save(lesson(
                cohort,
                instructor,
                "Elapsed",
                NOW.minusSeconds(10_800),
                NOW.minusSeconds(7_200),
                LessonStatus.SCHEDULED
        ));
        Lesson current = lessonRepository.save(lesson(
                cohort,
                instructor,
                "Current",
                NOW.minusSeconds(3_600),
                NOW.plusSeconds(3_600),
                LessonStatus.SCHEDULED
        ));
        Lesson future = lessonRepository.save(lesson(
                cohort,
                instructor,
                "Future",
                NOW.plusSeconds(7_200),
                NOW.plusSeconds(10_800),
                LessonStatus.SCHEDULED
        ));
        Lesson cancelled = lessonRepository.save(lesson(
                cohort,
                instructor,
                "Cancelled",
                NOW.minusSeconds(18_000),
                NOW.minusSeconds(14_400),
                LessonStatus.CANCELLED
        ));

        Activity expiredPublished = activityRepository.save(activity(
                current,
                creator,
                "Expired published",
                NOW.minusSeconds(60),
                ActivityStatus.PUBLISHED
        ));
        Activity futurePublished = activityRepository.save(activity(
                future,
                creator,
                "Future published",
                NOW.plusSeconds(3_600),
                ActivityStatus.PUBLISHED
        ));
        Activity expiredDraft = activityRepository.save(activity(
                elapsed,
                creator,
                "Expired draft",
                NOW.minusSeconds(60),
                ActivityStatus.DRAFT
        ));

        AcademicLifecycleService.UpdateSummary summary = service.updateStatuses(NOW);

        assertEquals(1, summary.startedLessons());
        assertEquals(1, summary.completedLessons());
        assertEquals(1, summary.closedActivities());
        assertEquals(LessonStatus.COMPLETED, lessonRepository.findById(elapsed.getId()).orElseThrow().getStatus());
        assertEquals(LessonStatus.IN_PROGRESS, lessonRepository.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(LessonStatus.SCHEDULED, lessonRepository.findById(future.getId()).orElseThrow().getStatus());
        assertEquals(LessonStatus.CANCELLED, lessonRepository.findById(cancelled.getId()).orElseThrow().getStatus());
        assertEquals(ActivityStatus.CLOSED, activityRepository.findById(expiredPublished.getId()).orElseThrow().getStatus());
        assertEquals(ActivityStatus.PUBLISHED, activityRepository.findById(futurePublished.getId()).orElseThrow().getStatus());
        assertEquals(ActivityStatus.DRAFT, activityRepository.findById(expiredDraft.getId()).orElseThrow().getStatus());
    }

    private Person person() {
        Person person = new Person();
        person.setAddress(address("Lifecycle Street"));
        person.setFullName("Lifecycle instructor");
        person.setTaxId("91000000001");
        person.setPhoneNumber("41999999999");
        person.setBirthDate(LocalDate.of(1990, 1, 1));
        return person;
    }

    private Address address(String street) {
        Address address = new Address();
        address.setPostalCode("80000000");
        address.setStreet(street);
        address.setStreetNumber("100");
        address.setDistrict("Center");
        address.setCity("Curitiba");
        address.setStateCode("PR");
        address.setCountryCode("BR");
        return address;
    }

    private Cohort cohort() {
        Cohort cohort = new Cohort();
        cohort.setCode("LIFECYCLE-TEST");
        cohort.setName("Lifecycle test cohort");
        cohort.setDefaultWeekday(4);
        cohort.setShiftCode("AFTERNOON");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));
        cohort.setStatus(CohortStatus.ACTIVE);
        return cohort;
    }

    private Lesson lesson(
            Cohort cohort,
            Person instructor,
            String title,
            Instant startsAt,
            Instant endsAt,
            LessonStatus status
    ) {
        Lesson lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructor);
        lesson.setTitle(title);
        lesson.setStartsAt(startsAt);
        lesson.setEndsAt(endsAt);
        lesson.setDeliveryMode(DeliveryMode.ONSITE);
        lesson.setRoom("Room 1");
        lesson.setStatus(status);
        return lesson;
    }

    private Activity activity(
            Lesson lesson,
            User creator,
            String title,
            Instant dueAt,
            ActivityStatus status
    ) {
        Activity activity = new Activity();
        activity.setLesson(lesson);
        activity.setCreatedByUser(creator);
        activity.setTitle(title);
        activity.setDescription("Lifecycle test activity");
        activity.setAvailableAt(dueAt.minusSeconds(3_600));
        activity.setDueAt(dueAt);
        activity.setStatus(status);
        return activity;
    }
}
