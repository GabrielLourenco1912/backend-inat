package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.model.Notification;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationTypeRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(ActivityDueReminderIntegrationTest.FixedClockConfiguration.class)
class ActivityDueReminderIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Autowired
    private ActivityDueReminderService service;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LearnerRepository learnerRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationTypeRepository organizationTypeRepository;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private CohortRepository cohortRepository;
    @Autowired
    private CohortEnrollmentRepository enrollmentRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private ActivitySubmissionRepository submissionRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationRecipientRepository recipientRepository;

    @Test
    void shouldNotifyOnlyLearnerWithoutDeliveredSubmissionAndOnlyOnce() {
        Organization employer = organizationRepository.save(organization());
        Cohort cohort = cohortRepository.save(cohort());
        LearnerAccount delivered = learnerAccount("Delivered", "92000000001", "REM-DELIVERED");
        LearnerAccount pending = learnerAccount("Pending", "92000000002", "REM-PENDING");
        enroll(delivered.learner(), employer, cohort);
        enroll(pending.learner(), employer, cohort);

        Lesson lesson = lessonRepository.save(lesson(cohort, delivered.person()));
        Activity activity = activityRepository.save(activity(lesson, delivered.user()));
        submissionRepository.save(submission(
                activity,
                delivered.learner(),
                SubmissionStatus.SUBMITTED
        ));
        submissionRepository.save(submission(
                activity,
                pending.learner(),
                SubmissionStatus.DRAFT
        ));

        assertEquals(
                List.of(activity.getId()),
                activityRepository.findDueReminderCandidateIds(
                        ActivityStatus.PUBLISHED,
                        NOW,
                        NOW.plus(ActivityDueReminderService.REMINDER_ADVANCE),
                        PageRequest.of(0, 10)
                )
        );

        assertTrue(service.process(activity.getId(), NOW));

        List<Notification> notifications = notificationRepository
                .findAllByContextTypeAndContextIdOrderByCreatedAtDesc(
                        "ACTIVITY",
                        activity.getId()
                );
        assertEquals(1, notifications.size());
        Notification notification = notifications.getFirst();
        assertEquals("ACTIVITY_DUE_SOON", notification.getNotificationType());
        assertEquals(activity.getDueAt(), notification.getExpiresAt());

        List<NotificationRecipient> recipients = recipientRepository.findAll();
        assertEquals(2, recipients.size());
        assertTrue(recipients.stream().allMatch(
                recipient -> recipient.getRecipientUser().getUlid().equals(pending.user().getUlid())
        ));
        assertEquals(
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                recipients.stream()
                        .map(NotificationRecipient::getChannel)
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertEquals(
                NotificationDeliveryStatus.DELIVERED,
                recipients.stream()
                        .filter(recipient -> recipient.getChannel() == NotificationChannel.IN_APP)
                        .findFirst()
                        .orElseThrow()
                        .getDeliveryStatus()
        );
        assertEquals(
                NotificationDeliveryStatus.PENDING,
                recipients.stream()
                        .filter(recipient -> recipient.getChannel() == NotificationChannel.EMAIL)
                        .findFirst()
                        .orElseThrow()
                        .getDeliveryStatus()
        );
        assertNotNull(activityRepository.findById(activity.getId()).orElseThrow()
                .getDueReminderProcessedAt());

        assertFalse(service.process(activity.getId(), NOW.plusSeconds(30)));
        assertTrue(activityRepository.findDueReminderCandidateIds(
                ActivityStatus.PUBLISHED,
                NOW.plusSeconds(30),
                NOW.plusSeconds(30).plus(ActivityDueReminderService.REMINDER_ADVANCE),
                PageRequest.of(0, 10)
        ).isEmpty());
        assertEquals(1, notificationRepository.count());
        assertEquals(2, recipientRepository.count());
    }

    @Test
    void shouldCancelOnlyOpenActivitiesAndExpireTheirPendingReminder() {
        Cohort cohort = cohortRepository.save(cohort());
        LearnerAccount account = learnerAccount(
                "Cancellation",
                "92000000003",
                "REM-CANCELLED"
        );
        Lesson lesson = lessonRepository.save(lesson(cohort, account.person()));

        Activity draft = activity(lesson, account.user());
        draft.setTitle("Draft activity");
        draft.setStatus(ActivityStatus.DRAFT);
        draft = activityRepository.save(draft);

        Activity published = activity(lesson, account.user());
        published.setTitle("Published activity");
        published = activityRepository.save(published);

        Activity closed = activity(lesson, account.user());
        closed.setTitle("Closed activity");
        closed.setStatus(ActivityStatus.CLOSED);
        closed = activityRepository.save(closed);

        Notification reminder = new Notification();
        reminder.setCreatedByUser(account.user());
        reminder.setAudienceType(NotificationAudienceType.USER);
        reminder.setAudienceId(account.user().getUlid());
        reminder.setNotificationType("ACTIVITY_DUE_SOON");
        reminder.setTitle("Pending reminder");
        reminder.setMessage("This reminder must expire with the lesson");
        reminder.setContextType("ACTIVITY");
        reminder.setContextId(published.getId());
        reminder.setPriority(NotificationPriority.HIGH);
        reminder.setExpiresAt(NOW.plusSeconds(3_600));
        reminder = notificationRepository.save(reminder);

        assertEquals(2, activityRepository.cancelOpenActivitiesByLessonId(
                lesson.getId(),
                Set.of(ActivityStatus.DRAFT, ActivityStatus.PUBLISHED),
                ActivityStatus.CANCELLED,
                NOW
        ));
        assertEquals(1, notificationRepository.expireActiveActivityNotificationsForLesson(
                lesson.getId(),
                "ACTIVITY_DUE_SOON",
                "ACTIVITY",
                NOW
        ));

        assertEquals(
                ActivityStatus.CANCELLED,
                activityRepository.findById(draft.getId()).orElseThrow().getStatus()
        );
        assertEquals(
                ActivityStatus.CANCELLED,
                activityRepository.findById(published.getId()).orElseThrow().getStatus()
        );
        assertEquals(
                ActivityStatus.CLOSED,
                activityRepository.findById(closed.getId()).orElseThrow().getStatus()
        );
        assertEquals(
                NOW,
                notificationRepository.findById(reminder.getId()).orElseThrow().getExpiresAt()
        );
    }

    private LearnerAccount learnerAccount(String name, String taxId, String registration) {
        Person person = new Person();
        person.setAddress(address(name + " Street"));
        person.setFullName(name + " learner");
        person.setTaxId(taxId);
        person.setPhoneNumber("41999999999");
        person.setBirthDate(LocalDate.of(2000, 1, 1));
        person = personRepository.save(person);

        User user = new User(name, name.toLowerCase() + "@example.com", "hash");
        user.setPerson(person);
        user = userRepository.save(user);

        Learner learner = new Learner();
        learner.setPerson(person);
        learner = learnerRepository.save(learner);
        return new LearnerAccount(person, user, learner);
    }

    private void enroll(Learner learner, Organization employer, Cohort cohort) {
        Contract contract = new Contract();
        contract.setLearner(learner);
        contract.setEmployer(employer);
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        contract.setMonthlySalary(new BigDecimal("1500.00"));
        contract.setWeeklyWorkloadMinutes(1200);
        contract.setStatus(ContractStatus.ACTIVE);
        contract = contractRepository.save(contract);

        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setContract(contract);
        enrollment.setCohort(cohort);
        enrollment.setStartDate(LocalDate.of(2026, 1, 1));
        enrollment.setEndDate(LocalDate.of(2026, 12, 31));
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollmentRepository.save(enrollment);
    }

    private Organization organization() {
        Organization organization = new Organization();
        organization.setAddress(address("Employer Street"));
        organization.getOrganizationTypes().add(organizationTypeRepository.findById(OrganizationType.EMPLOYER).orElseThrow());
        organization.setLegalName("Reminder Employer Ltda");
        organization.setTradeName("Reminder Employer");
        organization.setTaxId("93000000000001");
        organization.setContactEmail("employer@example.com");
        organization.setPhoneNumber("4133333333");
        return organization;
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
        cohort.setCode("REMINDER-TEST");
        cohort.setName("Reminder test cohort");
        cohort.setDefaultWeekday(4);
        cohort.setShiftCode("AFTERNOON");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));
        cohort.setStatus(CohortStatus.ACTIVE);
        return cohort;
    }

    private Lesson lesson(Cohort cohort, Person instructor) {
        Lesson lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructor);
        lesson.setTitle("Reminder lesson");
        lesson.setStartsAt(NOW.minusSeconds(3_600));
        lesson.setEndsAt(NOW.plusSeconds(14_400));
        lesson.setDeliveryMode(DeliveryMode.ONLINE);
        lesson.setMeetingUrl("https://example.com/reminder");
        lesson.setStatus(LessonStatus.IN_PROGRESS);
        return lesson;
    }

    private Activity activity(Lesson lesson, User creator) {
        Activity activity = new Activity();
        activity.setLesson(lesson);
        activity.setCreatedByUser(creator);
        activity.setTitle("Reminder activity");
        activity.setDescription("Activity with a three-hour reminder");
        activity.setAvailableAt(NOW.minusSeconds(3_600));
        activity.setDueAt(NOW.plus(ActivityDueReminderService.REMINDER_ADVANCE));
        activity.setStatus(ActivityStatus.PUBLISHED);
        return activity;
    }

    private ActivitySubmission submission(
            Activity activity,
            Learner learner,
            SubmissionStatus status
    ) {
        ActivitySubmission submission = new ActivitySubmission();
        submission.setActivity(activity);
        submission.setLearner(learner);
        submission.setTextAnswer(status == SubmissionStatus.DRAFT ? "Draft" : "Delivered");
        submission.setStatus(status);
        if (status != SubmissionStatus.DRAFT) {
            submission.setSubmittedAt(NOW.minusSeconds(60));
        }
        return submission;
    }

    private record LearnerAccount(Person person, User user, Learner learner) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock reminderTestClock() {
            return Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
        }
    }
}
