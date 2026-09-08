package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationAudienceIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRecipientRepository recipientRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void shouldExpandCohortAudienceOnlyToItsActiveLearnerUsers() {
        Cohort cohort = persistCohort("COHORT-NOTIFICATION");
        User cohortUser = persistLearnerUser("11122233344", "cohort@example.com");
        User otherUser = persistLearnerUser("55566677788", "other@example.com");
        enroll(cohort, cohortUser.getPerson());
        entityManager.flush();

        NotificationDtos.Response response = notificationService.create(
                new NotificationDtos.CreateRequest(
                        "COHORT_MESSAGE",
                        "Class message",
                        "Message for the active class.",
                        null,
                        "COHORT",
                        cohort.getId(),
                        null,
                        NotificationPriority.NORMAL,
                        null,
                        null,
                        new NotificationDtos.AudienceRequest(
                                NotificationAudienceType.COHORT,
                                cohort.getId()
                        ),
                        Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
                ),
                null
        );
        entityManager.flush();

        List<NotificationRecipient> recipients = recipientRepository.findAll().stream()
                .filter(value -> value.getNotification().getId().equals(response.id()))
                .toList();
        assertEquals(2, recipients.size());
        assertEquals(
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                recipients.stream().map(NotificationRecipient::getChannel).collect(
                        java.util.stream.Collectors.toSet()
                )
        );
        assertEquals(
                Set.of(cohortUser.getUlid()),
                recipients.stream().map(value -> value.getRecipientUser().getUlid()).collect(
                        java.util.stream.Collectors.toSet()
                )
        );
        assertEquals(NotificationAudienceType.COHORT, response.audience().type());
        assertEquals(cohort.getId(), response.audience().targetId());
        // Keeps the unrelated user managed so the assertion explicitly covers exclusion.
        assertEquals(UserAccountStatus.ACTIVE, otherUser.getStatus());
    }

    @Test
    void shouldDeletePendingNotificationWithItsRecipients() {
        User user = persistLearnerUser("99988877766", "pending@example.com");
        entityManager.flush();

        NotificationDtos.Response response = notificationService.create(
                new NotificationDtos.CreateRequest(
                        "PENDING_MESSAGE",
                        "Pending message",
                        "Message waiting for external delivery.",
                        null,
                        null,
                        null,
                        null,
                        NotificationPriority.NORMAL,
                        null,
                        null,
                        new NotificationDtos.AudienceRequest(
                                NotificationAudienceType.USER,
                                user.getUlid()
                        ),
                        Set.of(NotificationChannel.EMAIL)
                ),
                null
        );
        entityManager.flush();

        notificationService.delete(response.id());
        entityManager.flush();

        assertFalse(notificationRepository.existsById(response.id()));
        assertFalse(recipientRepository.findAll().stream().anyMatch(
                value -> value.getNotification().getId().equals(response.id())
        ));
    }

    private Cohort persistCohort(String code) {
        Cohort cohort = new Cohort();
        cohort.setCode(code);
        cohort.setName("Notification cohort");
        cohort.setDefaultWeekday(4);
        cohort.setShiftCode("AFTERNOON");
        cohort.setStartDate(LocalDate.now().minusMonths(1));
        cohort.setEndDate(LocalDate.now().plusMonths(1));
        cohort.setStatus(CohortStatus.ACTIVE);
        entityManager.persist(cohort);
        return cohort;
    }

    private User persistLearnerUser(String taxId, String email) {
        Person person = person(taxId, email);
        entityManager.persist(person);

        Learner learner = new Learner();
        learner.setPerson(person);
        learner.setRegistrationNumber("REG-" + taxId);
        learner.setStatus(RecordStatus.ACTIVE);
        entityManager.persist(learner);

        User user = new User("Learner " + taxId, email, "hash");
        user.setPerson(person);
        user.setStatus(UserAccountStatus.ACTIVE);
        entityManager.persist(user);
        return user;
    }

    private void enroll(Cohort cohort, Person person) {
        Learner learner = entityManager.createQuery(
                        "select l from Learner l where l.person = :person",
                        Learner.class
                )
                .setParameter("person", person)
                .getSingleResult();

        Organization employer = new Organization();
        employer.setAddress(address());
        employer.setOrganizationType(OrganizationType.EMPLOYER);
        employer.setLegalName("Notification Employer Ltda");
        employer.setTaxId("12345678000190");
        employer.setContactEmail("employer@example.com");
        employer.setPhoneNumber("41999999999");
        employer.setStatus(RecordStatus.ACTIVE);
        entityManager.persist(employer);

        Contract contract = new Contract();
        contract.setLearner(learner);
        contract.setEmployer(employer);
        contract.setStartDate(LocalDate.now().minusMonths(1));
        contract.setEndDate(LocalDate.now().plusMonths(1));
        contract.setMonthlySalary(new BigDecimal("1000.00"));
        contract.setWeeklyWorkloadMinutes(1200);
        contract.setStatus(ContractStatus.ACTIVE);
        entityManager.persist(contract);

        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setContract(contract);
        enrollment.setCohort(cohort);
        enrollment.setStartDate(LocalDate.now().minusDays(10));
        enrollment.setEndDate(LocalDate.now().plusDays(10));
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        entityManager.persist(enrollment);
    }

    private Person person(String taxId, String email) {
        Person person = new Person();
        person.setAddress(address());
        person.setFullName("Learner " + taxId);
        person.setTaxId(taxId);
        person.setContactEmail(email);
        person.setPhoneNumber("41999999999");
        person.setBirthDate(LocalDate.of(2000, 1, 1));
        return person;
    }

    private Address address() {
        Address address = new Address();
        address.setPostalCode("80000000");
        address.setStreet("Test Street");
        address.setStreetNumber("100");
        address.setDistrict("Center");
        address.setCity("Curitiba");
        address.setStateCode("PR");
        address.setCountryCode("BR");
        return address;
    }
}
