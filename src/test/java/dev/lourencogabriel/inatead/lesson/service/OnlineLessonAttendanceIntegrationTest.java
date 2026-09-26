package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.dto.ActivitySubmissionDtos;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.activity.service.ActivitySubmissionService;
import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnlineLessonAttendanceIntegrationTest {

    @Autowired
    private OnlineLessonAttendanceService service;
    @Autowired
    private ActivitySubmissionService submissionService;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LearnerRepository learnerRepository;
    @Autowired
    private CohortRepository cohortRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private LessonParticipantRepository participantRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private ActivitySubmissionRepository submissionRepository;
    @Autowired
    private AttendanceRecordRepository attendanceRepository;

    @Test
    void shouldPageLearnerAttendanceAndSelectEachUnrecordedOnlineLessonOnce() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Person instructor = personRepository.save(person(suffix));
        Person secondPerson = personRepository.save(person(UUID.randomUUID().toString().replace("-", "")));

        User user = new User("Attendance recorder", suffix + "@example.com", "hash");
        user.setPerson(instructor);
        user = userRepository.save(user);

        Learner firstLearner = new Learner();
        firstLearner.setPerson(instructor);
        firstLearner = learnerRepository.save(firstLearner);
        Learner secondLearner = new Learner();
        secondLearner.setPerson(secondPerson);
        secondLearner = learnerRepository.save(secondLearner);

        Cohort cohort = new Cohort();
        cohort.setCode("ATTEND-" + suffix.substring(0, 8));
        cohort.setName("Attendance query cohort");
        cohort.setDefaultWeekday(1);
        cohort.setShiftCode("AFTERNOON");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));
        cohort.setStatus(CohortStatus.ACTIVE);
        cohort = cohortRepository.save(cohort);

        Lesson earlier = lessonRepository.save(onlineLesson(
                cohort, instructor, "Earlier", "2026-09-06T15:00:00Z"
        ));
        Lesson later = lessonRepository.save(onlineLesson(
                cohort, instructor, "Later", "2026-09-07T15:00:00Z"
        ));
        LessonParticipant firstEarlier = participantRepository.save(participant(earlier, firstLearner));
        LessonParticipant secondEarlier = participantRepository.save(participant(earlier, secondLearner));
        LessonParticipant firstLater = participantRepository.save(participant(later, firstLearner));

        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        assertEquals(List.of(earlier.getId(), later.getId()), attendanceCandidates(now, 10));
        assertEquals(List.of(earlier.getId()), attendanceCandidates(now, 1));

        attendanceRepository.save(attendance(firstEarlier, user, now));
        attendanceRepository.save(attendance(firstLater, user, now));
        assertEquals(List.of(earlier.getId()), attendanceCandidates(now, 10));

        Page<AttendanceRecord> firstPage = attendanceRepository.findAllFiltered(
                null, null, firstLearner.getId(), null, false, null, null, PageRequest.of(0, 1)
        );
        Page<AttendanceRecord> secondPage = attendanceRepository.findAllFiltered(
                null, null, firstLearner.getId(), null, false, null, null, PageRequest.of(1, 1)
        );
        assertEquals(2, firstPage.getTotalElements());
        assertEquals(later.getId(), firstPage.getContent().getFirst().getLessonParticipant().getLesson().getId());
        assertEquals(earlier.getId(), secondPage.getContent().getFirst().getLessonParticipant().getLesson().getId());

        attendanceRepository.save(attendance(secondEarlier, user, now));
        assertEquals(List.of(), attendanceCandidates(now, 10));
    }

    @Test
    void shouldConvertAbsenceOnlyAfterEveryOnlineActivityIsGradedAboveSix() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Person person = personRepository.save(person(suffix));

        User user = new User("Online learner", suffix + "@example.com", "hash");
        user.setPerson(person);
        user = userRepository.save(user);

        Learner learner = new Learner();
        learner.setPerson(person);
        learner = learnerRepository.save(learner);

        Cohort cohort = new Cohort();
        cohort.setCode("ONLINE-" + suffix.substring(0, 8));
        cohort.setName("Online attendance cohort");
        cohort.setDefaultWeekday(1);
        cohort.setShiftCode("AFTERNOON");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));
        cohort.setStatus(CohortStatus.ACTIVE);
        cohort = cohortRepository.save(cohort);

        Lesson lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(person);
        lesson.setTitle("Online attendance lesson");
        lesson.setStartsAt(Instant.parse("2026-09-06T13:00:00Z"));
        lesson.setEndsAt(Instant.parse("2026-09-06T15:00:00Z"));
        lesson.setDeliveryMode(DeliveryMode.ONLINE);
        lesson.setMeetingUrl("https://example.com/online-lesson");
        lesson.setStatus(LessonStatus.COMPLETED);
        lesson = lessonRepository.save(lesson);

        LessonParticipant participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setStatus(ParticipationStatus.EXPECTED);
        participant = participantRepository.save(participant);

        Activity firstActivity = activityRepository.save(activity(
                lesson,
                user,
                "First activity"
        ));
        Activity secondActivity = activityRepository.save(activity(
                lesson,
                user,
                "Second activity"
        ));

        ActivitySubmission firstSubmission = submissionRepository.save(submission(
                firstActivity,
                learner
        ));
        ActivitySubmission secondSubmission = submissionRepository.save(submission(
                secondActivity,
                learner
        ));
        assertEquals(List.of(), presenceCandidates());
        assertFalse(service.recordQualifiedAttendance(
                participant.getId(), lesson.getEndsAt().plusSeconds(30)
        ));
        assertFalse(attendanceRepository.existsByLessonParticipant_Id(participant.getId()));

        assertEquals(
                1,
                service.recordMissingExpectedAttendance(
                        lesson.getId(),
                        lesson.getEndsAt().plusSeconds(60)
                )
        );
        AttendanceRecord absence = attendanceRepository
                .findByLessonParticipant_Id(participant.getId())
                .orElseThrow();
        assertEquals(AttendanceStatus.ABSENT, absence.getStatus());

        submissionService.grade(firstSubmission.getId(),
                new ActivitySubmissionDtos.GradeRequest(new BigDecimal("7.00"), null, SubmissionStatus.GRADED),
                user.getUlid());
        assertEquals(List.of(), presenceCandidates());
        submissionService.grade(secondSubmission.getId(),
                new ActivitySubmissionDtos.GradeRequest(new BigDecimal("8.00"), null, SubmissionStatus.GRADED),
                user.getUlid());
        assertEquals(List.of(participant.getId()), presenceCandidates());
        assertTrue(service.recordQualifiedAttendance(
                participant.getId(), lesson.getEndsAt().plusSeconds(120)
        ));

        List<AttendanceRecord> records = attendanceRepository
                .findAllByLessonParticipant_Lesson_IdOrderByRecordedAtAsc(lesson.getId());
        assertEquals(1, records.size());
        AttendanceRecord attendance = records.getFirst();
        assertEquals(AttendanceStatus.PRESENT, attendance.getStatus());
        assertEquals(lesson.getStartsAt(), attendance.getCheckInAt());
        assertEquals(lesson.getEndsAt(), attendance.getCheckOutAt());
        assertEquals(participant.getId(), attendance.getLessonParticipant().getId());
        assertEquals(List.of(), presenceCandidates());
    }

    private List<String> presenceCandidates() {
        return participantRepository.findOnlinePresenceCandidateIds(
                "",
                ParticipationStatus.EXPECTED,
                DeliveryMode.ONLINE,
                Set.of(LessonStatus.IN_PROGRESS, LessonStatus.COMPLETED),
                OnlineLessonAttendanceService.REQUIRED_ACTIVITY_STATUSES,
                SubmissionStatus.GRADED,
                OnlineLessonAttendanceService.MINIMUM_PASSING_SCORE,
                AttendanceStatus.ABSENT,
                OnlineLessonAttendanceService.AUTOMATIC_ABSENCE_NOTES,
                PageRequest.of(0, 200)
        );
    }

    private List<String> attendanceCandidates(Instant now, int pageSize) {
        return lessonRepository.findOnlineAttendanceCandidateIds(
                now, DeliveryMode.ONLINE, LessonStatus.CANCELLED,
                ParticipationStatus.EXPECTED, PageRequest.of(0, pageSize)
        );
    }

    private Lesson onlineLesson(Cohort cohort, Person instructor, String title, String endsAt) {
        Lesson lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructor);
        lesson.setTitle(title);
        lesson.setStartsAt(Instant.parse(endsAt).minusSeconds(7_200));
        lesson.setEndsAt(Instant.parse(endsAt));
        lesson.setDeliveryMode(DeliveryMode.ONLINE);
        lesson.setMeetingUrl("https://example.com/online-lesson");
        lesson.setStatus(LessonStatus.COMPLETED);
        return lesson;
    }

    private LessonParticipant participant(Lesson lesson, Learner learner) {
        LessonParticipant participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setStatus(ParticipationStatus.EXPECTED);
        return participant;
    }

    private AttendanceRecord attendance(LessonParticipant participant, User user, Instant recordedAt) {
        AttendanceRecord attendance = new AttendanceRecord();
        attendance.setLessonParticipant(participant);
        attendance.setRecordedByUser(user);
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setRecordedAt(recordedAt);
        return attendance;
    }

    private Person person(String suffix) {
        Address address = new Address();
        address.setPostalCode("80000000");
        address.setStreet("Online Test Street");
        address.setStreetNumber("100");
        address.setDistrict("Center");
        address.setCity("Curitiba");
        address.setStateCode("PR");
        address.setCountryCode("BR");

        Person person = new Person();
        person.setAddress(address);
        person.setFullName("Online learner");
        person.setTaxId(suffix.substring(0, 11).replaceAll("[a-f]", "1"));
        person.setPhoneNumber("41999999999");
        person.setBirthDate(LocalDate.of(2000, 1, 1));
        return person;
    }

    private Activity activity(Lesson lesson, User user, String title) {
        Activity activity = new Activity();
        activity.setLesson(lesson);
        activity.setCreatedByUser(user);
        activity.setTitle(title);
        activity.setDescription("Required online activity");
        activity.setAvailableAt(lesson.getStartsAt());
        activity.setDueAt(lesson.getEndsAt());
        activity.setMaxScore(new BigDecimal("10.00"));
        activity.setStatus(ActivityStatus.PUBLISHED);
        return activity;
    }

    private ActivitySubmission submission(Activity activity, Learner learner) {
        ActivitySubmission submission = new ActivitySubmission();
        submission.setActivity(activity);
        submission.setLearner(learner);
        submission.setTextAnswer("Delivered");
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(Instant.parse("2026-09-06T14:30:00Z"));
        return submission;
    }
}
