package dev.lourencogabriel.inatead.project;

import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.dto.LessonParticipantDtos;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.service.LessonParticipantService;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.model.OrganizationTypeEntry;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:apprenticeship_policy_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class ApprenticeshipPolicyIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);
    @Autowired private EntityManager entities;
    @Autowired private LessonParticipantService participants;
    @Autowired private LessonParticipantRepository participantRepository;
    @Autowired private ApprenticeshipPolicy policy;
    private Organization employer;
    private Person instructor;
    private Cohort cohort;

    @BeforeEach
    void setUp() {
        instructor = person();
        employer = new Organization();
        employer.getOrganizationTypes().add(entities.getReference(OrganizationTypeEntry.class, OrganizationType.EMPLOYER));
        employer.setLegalName("Apprenticeship employer");
        employer.setTaxId(identifier(14));
        employer.setContactEmail("employer@example.com");
        employer.setPhoneNumber("11999999999");
        employer.setAddress(address());
        entities.persist(employer);
        cohort = cohort();
    }

    @Test
    void onlineRosterIncludesOnly30HoursAndKeepsMixedEnrollments() {
        var twenty = enrollment(contract(1200));
        var thirty = enrollment(contract(1800));
        enrollment(contract(2400));
        var lesson = lesson(DeliveryMode.ONLINE);

        var first = participants.generateRegularRoster(lesson.getId());
        var second = participants.generateRegularRoster(lesson.getId());

        assertEquals(1, first.createdCount());
        assertEquals(thirty.getContract().getLearner().getId(), first.participants().getFirst().learnerId());
        assertEquals(0, second.createdCount());
        assertEquals(1, second.existingCount());
        assertEquals(EnrollmentStatus.ACTIVE, twenty.getStatus());
        assertEquals(cohort.getId(), twenty.getCohort().getId());
    }

    @Test
    void onsiteRosterKeepsBothModels() {
        enrollment(contract(1200));
        enrollment(contract(1800));

        assertEquals(2, participants.generateRegularRoster(lesson(DeliveryMode.ONSITE).getId()).createdCount());
    }

    @Test
    void onlineRosterCanBeEmptyWhenTheCohortHasOnly20Hours() {
        enrollment(contract(1200));

        assertEquals(0, participants.generateRegularRoster(lesson(DeliveryMode.ONLINE).getId()).createdCount());
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationType.class, names = {"REGULAR", "MAKEUP", "EXTRA"})
    void manualParticipationRejects20Hours(ParticipationType type) {
        var enrollment = enrollment(contract(1200));
        var lesson = lesson(DeliveryMode.ONLINE);

        var error = assertThrows(BusinessRuleException.class, () -> participants.create(
                request(lesson, enrollment.getContract(), type == ParticipationType.REGULAR ? enrollment : null, type), null));

        assertTrue(error.getMessage().contains("30-hour"));
        assertTrue(participantRepository.findAllByLesson_Id(lesson.getId()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationType.class, names = {"REGULAR", "MAKEUP", "EXTRA"})
    void manualParticipationAccepts30Hours(ParticipationType type) {
        var enrollment = enrollment(contract(1800));
        var lesson = lesson(DeliveryMode.ONLINE);

        var response = participants.create(request(lesson, enrollment.getContract(),
                type == ParticipationType.REGULAR ? enrollment : null, type), null);

        assertEquals(enrollment.getContract().getLearner().getId(), response.learnerId());
        assertEquals(type, response.participationType());
    }

    @Test
    void sourceEnrollmentCannotBorrowEligibilityFromAnotherContract() {
        var twenty = contract(1200);
        var thirty = contract(1800);
        thirty.setLearner(twenty.getLearner());
        var source = enrollment(twenty);

        assertThrows(BusinessRuleException.class, () -> participants.create(
                request(lesson(DeliveryMode.ONLINE), twenty, source, ParticipationType.REGULAR), null));
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"DRAFT", "SUSPENDED", "ENDED", "CANCELLED"})
    void manualExtrasRequireAnActiveContract(ContractStatus status) {
        var contract = contract(1800);
        contract.setStatus(status);

        assertThrows(BusinessRuleException.class, () -> participants.create(
                request(lesson(DeliveryMode.ONLINE), contract, null, ParticipationType.EXTRA), null));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void contractMustCoverTheLessonDate(int offset) {
        var contract = contract(1800);
        contract.setStartDate(DATE.plusDays(offset));
        contract.setEndDate(DATE.plusDays(offset));

        assertThrows(BusinessRuleException.class, () -> participants.create(
                request(lesson(DeliveryMode.ONLINE), contract, null, ParticipationType.EXTRA), null));
    }

    @Test
    void contractDateBoundariesUseTheApplicationTimeZoneAndAreInclusive() {
        var contract = contract(1800);
        contract.setStartDate(DATE);
        contract.setEndDate(DATE);
        var lesson = lesson(DeliveryMode.ONLINE);
        // September 11 UTC is still September 10 in São Paulo.
        lesson.setStartsAt(Instant.parse("2026-09-11T01:00:00Z"));
        lesson.setEndsAt(Instant.parse("2026-09-11T02:00:00Z"));

        assertDoesNotThrow(() -> participants.create(request(lesson, contract, null, ParticipationType.EXTRA), null));
    }

    @Test
    void reactivationRevalidatesTheWorkload() {
        var contract = contract(1800);
        var lesson = lesson(DeliveryMode.ONLINE);
        var response = participants.create(request(lesson, contract, null, ParticipationType.EXTRA), null);
        var participant = participantRepository.findById(response.id()).orElseThrow();
        participant.setStatus(ParticipationStatus.CANCELLED);
        contract.setWeeklyWorkloadMinutes(1200);

        assertThrows(BusinessRuleException.class, () -> participants.update(response.id(),
                request(lesson, contract, null, ParticipationType.EXTRA), null));
    }

    @Test
    void reassignmentToOnlineRejects20Hours() {
        var contract = contract(1200);
        var enrollment = enrollment(contract);
        var sourceLesson = lesson(DeliveryMode.ONSITE);
        var source = participants.create(request(sourceLesson, contract, enrollment, ParticipationType.REGULAR), null);
        cohort = cohort();
        var targetLesson = lesson(DeliveryMode.ONLINE);
        var actor = new User("Staff", identifier(10) + "@example.com", "hash");
        actor.setPerson(instructor);
        entities.persist(actor);

        var error = assertThrows(BusinessRuleException.class, () -> participants.reassign(
                new LessonParticipantDtos.ReassignRequest(source.id(), targetLesson.getId(), "Day reassignment"), actor.getUlid()));

        assertTrue(error.getMessage().contains("30-hour"));
        assertTrue(participantRepository.findAllByLesson_Id(targetLesson.getId()).isEmpty());
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void openOnlineParticipationBlocksWorkloadReductionWithOrWithoutEnrollment(boolean withEnrollment) {
        var contract = contract(1800);
        var source = withEnrollment ? enrollment(contract) : null;
        participants.create(request(lesson(DeliveryMode.ONLINE), contract, source,
                withEnrollment ? ParticipationType.REGULAR : ParticipationType.EXTRA), null);

        assertThrows(BusinessRuleException.class, () -> policy.validateWorkloadChange(contract, 1200));
        assertDoesNotThrow(() -> policy.validateWorkloadChange(contract, 1800));
        assertEquals(1800, contract.getWeeklyWorkloadMinutes());
    }

    @ParameterizedTest
    @EnumSource(value = LessonStatus.class, names = {"COMPLETED", "CANCELLED"})
    void closedLessonsDoNotBlockWorkloadChanges(LessonStatus status) {
        var contract = contract(1800);
        var lesson = lesson(DeliveryMode.ONLINE);
        participants.create(request(lesson, contract, null, ParticipationType.EXTRA), null);
        lesson.setStatus(status);

        assertDoesNotThrow(() -> policy.validateWorkloadChange(contract, 1200));
    }

    @Test
    void cancelledParticipationDoesNotBlockWorkloadChanges() {
        var contract = contract(1800);
        var response = participants.create(request(lesson(DeliveryMode.ONLINE), contract, null, ParticipationType.EXTRA), null);
        participantRepository.findById(response.id()).orElseThrow().setStatus(ParticipationStatus.CANCELLED);

        assertDoesNotThrow(() -> policy.validateWorkloadChange(contract, 1200));
    }

    private LessonParticipantDtos.Request request(Lesson lesson, Contract contract, CohortEnrollment source, ParticipationType type) {
        return new LessonParticipantDtos.Request(lesson.getId(), contract.getLearner().getId(),
                source == null ? null : source.getId(), type, ParticipationStatus.EXPECTED,
                type == ParticipationType.REGULAR ? null : "Additional lesson");
    }

    private Contract contract(int minutes) {
        var learner = new Learner();
        learner.setPerson(person());
        learner.setRegistrationNumber(identifier(20));
        learner.setHasCompletedHighSchool(true);
        entities.persist(learner);
        var contract = new Contract();
        contract.setLearner(learner);
        contract.setEmployer(employer);
        contract.setStartDate(DATE.minusMonths(1));
        contract.setEndDate(DATE.plusMonths(1));
        contract.setMonthlySalary(new BigDecimal("1000.00"));
        contract.setWeeklyWorkloadMinutes(minutes);
        contract.setStatus(ContractStatus.ACTIVE);
        entities.persist(contract);
        return contract;
    }

    private CohortEnrollment enrollment(Contract contract) {
        var enrollment = new CohortEnrollment();
        enrollment.setContract(contract);
        enrollment.setCohort(cohort);
        enrollment.setStartDate(contract.getStartDate());
        enrollment.setEndDate(contract.getEndDate());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        entities.persist(enrollment);
        return enrollment;
    }

    private Cohort cohort() {
        var cohort = new Cohort();
        cohort.setCode(identifier(20));
        cohort.setName("Mixed workload cohort");
        cohort.setDefaultWeekday(4);
        cohort.setShiftCode("MORNING");
        cohort.setStartDate(DATE.minusMonths(1));
        cohort.setEndDate(DATE.plusMonths(1));
        cohort.setStatus(CohortStatus.ACTIVE);
        entities.persist(cohort);
        return cohort;
    }

    private Lesson lesson(DeliveryMode mode) {
        var lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructor);
        lesson.setTitle("Apprenticeship lesson");
        lesson.setStartsAt(Instant.parse("2026-09-10T13:00:00Z"));
        lesson.setEndsAt(Instant.parse("2026-09-10T15:00:00Z"));
        lesson.setDeliveryMode(mode);
        lesson.setRoom(mode == DeliveryMode.ONSITE ? "Room 1" : null);
        entities.persist(lesson);
        return lesson;
    }

    private Person person() {
        var person = new Person();
        person.setFullName("Apprenticeship test person");
        person.setTaxId(identifier(11));
        person.setBirthDate(LocalDate.of(2000, 1, 1));
        person.setPhoneNumber("11999999999");
        person.setStatus(RecordStatus.ACTIVE);
        person.setAddress(address());
        entities.persist(person);
        return person;
    }

    private Address address() {
        var address = new Address();
        address.setPostalCode("01001000");
        address.setStreet("Test street");
        address.setStreetNumber("1");
        address.setDistrict("Centro");
        address.setCity("São Paulo");
        address.setStateCode("SP");
        return address;
    }

    private String identifier(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
