package dev.lourencogabriel.inatead.search;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.service.UserRoleService;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.model.OrganizationTypeEntry;
import dev.lourencogabriel.inatead.shared.model.*;
import java.time.Instant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:portal_search_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class PortalSearchIntegrationTest {
    static final AtomicInteger ids = new AtomicInteger(960000);
    @Autowired PortalSearchService search;
    @Autowired Validator validator;
    @Autowired PersonService people;
    @Autowired EntityManager em;
    @Autowired UserRoleService grants;
    @Autowired RoleRepository roles;
    @Autowired dev.lourencogabriel.inatead.learner.service.LearnerService learners;
    @Autowired dev.lourencogabriel.inatead.contract.service.ContractService contracts;
    @Autowired dev.lourencogabriel.inatead.lesson.service.LessonService lessons;
    @Autowired dev.lourencogabriel.inatead.lesson.service.LessonParticipantService participants;
    @Autowired dev.lourencogabriel.inatead.activity.service.ActivitySubmissionService submissions;

    @Test void learnerAndContractResponsesIncludeLinkedNamesWithoutChangingIds() {
        var learner = em.createQuery("select learner from Learner learner", Learner.class).setMaxResults(1).getSingleResult();
        var learnerResponse = learners.findById(learner.getId());
        assertEquals(learner.getId(), learnerResponse.id());
        assertEquals(learner.getPerson().getFullName(), learnerResponse.fullName());

        var contract = em.createQuery("select contract from Contract contract", Contract.class).setMaxResults(1).getSingleResult();
        var response = contracts.findById(contract.getId());
        assertEquals(contract.getLearner().getId(), response.learnerId());
        assertEquals(contract.getLearner().getPerson().getFullName(), response.learnerName());
        assertEquals(contract.getLearner().getRegistrationNumber(), response.learnerRegistrationNumber());
        assertEquals(contract.getEmployer().getDisplayName(), response.employerName());
        assertEquals(contract.getSchool() == null ? null : contract.getSchool().getDisplayName(), response.schoolName());
    }

    @Test void lessonRosterAndSubmissionsIncludeNamesAndRegistrationNumbers() {
        var learner = em.createQuery("select learner from Learner learner", Learner.class).setMaxResults(1).getSingleResult();
        String learnerName = learner.getPerson().getFullName();
        var cohort = em.createQuery("select cohort from Cohort cohort", Cohort.class).setMaxResults(1).getSingleResult();
        var instructor = person("Instrutor de referências", LocalDate.of(1990, 1, 1), PersonTypeCode.INSTRUCTOR);
        var user = new User("Instrutor", ids.incrementAndGet() + "@search.test", "hash");
        user.setPerson(instructor);
        em.persist(user);
        var lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructor);
        lesson.setTitle("Aula de referências");
        lesson.setDescription("Objetivos e conteúdo da aula");
        lesson.setStartsAt(Instant.parse("2026-09-10T13:00:00Z"));
        lesson.setEndsAt(Instant.parse("2026-09-10T15:00:00Z"));
        lesson.setDeliveryMode(DeliveryMode.ONLINE);
        em.persist(lesson);
        var participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setParticipationType(ParticipationType.REGULAR);
        participant.setStatus(ParticipationStatus.EXPECTED);
        em.persist(participant);
        var activity = new dev.lourencogabriel.inatead.activity.model.Activity();
        activity.setLesson(lesson);
        activity.setCreatedByUser(user);
        activity.setTitle("Atividade de referências");
        activity.setDescription("Enunciado");
        activity.setAvailableAt(lesson.getStartsAt());
        activity.setDueAt(lesson.getEndsAt());
        activity.setStatus(ActivityStatus.PUBLISHED);
        em.persist(activity);
        var submission = new dev.lourencogabriel.inatead.activity.model.ActivitySubmission();
        submission.setActivity(activity);
        submission.setLearner(learner);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        em.persist(submission);
        em.flush();
        em.clear();

        var response = lessons.findById(lesson.getId());
        assertEquals(cohort.getCode(), response.cohortCode());
        assertEquals(cohort.getName(), response.cohortName());
        assertEquals(instructor.getFullName(), response.instructorName());
        var roster = participants.findByLesson(lesson.getId());
        assertEquals(learnerName, roster.getFirst().learnerName());
        assertEquals(learner.getRegistrationNumber(), roster.getFirst().learnerRegistrationNumber());
        var deliveries = submissions.findByActivity(activity.getId());
        assertEquals(learnerName, deliveries.getFirst().learnerName());
        assertEquals(learner.getRegistrationNumber(), deliveries.getFirst().learnerRegistrationNumber());
    }

    private Person person(String name, LocalDate birth, PersonTypeCode type) {
        var dto = people.create(new PersonDtos.Request(new AddressDtos.Request("83200000", "Rua teste", "10", null, "Centro", "Paranagua", "PR", "BR"),
                name, String.format("%011d", ids.incrementAndGet()), null, "41999999999", birth, null, RecordStatus.ACTIVE, Set.of(type)));
        return em.find(Person.class, dto.id());
    }
    private Authentication actor(String role) {
        var user = new User("Test " + role, ids.incrementAndGet() + "@search.test", "hash");
        user.setPerson(person("Actor", LocalDate.of(1990, 1, 1), PersonTypeCode.valueOf(role)));
        em.persist(user); em.flush();
        var roleId = roles.findAll().stream().filter(r -> r.getCode().equals(role)).findFirst().orElseThrow().getId();
        grants.grant(user.getUlid(), roleId, null);
        em.flush(); em.clear();
        return new UsernamePasswordAuthenticationToken(user.getUlid(), "n/a", List.of());
    }
    @Test void searchFiltersBeforePaginationAndReturnsFilteredTotals() {
        var admin = actor("ADMIN");
        for (int i=0; i<30; i++) person("Outra pessoa " + i, LocalDate.of(1990,1,1), PersonTypeCode.GUARDIAN);
        for (int i=0; i<12; i++) person("Maria busca " + i, LocalDate.of(1990,1,1), PersonTypeCode.GUARDIAN);
        var first = search.search("people", "MARIA BUSCA", 0, 5, null, null, "guardian", null, true, admin);
        var second = search.search("people", "MARIA BUSCA", 1, 5, null, null, "guardian", null, true, admin);
        assertEquals(12, first.totalElements()); assertEquals(3, first.totalPages()); assertEquals(5, first.content().size());
        assertTrue(first.content().stream().noneMatch(second.content()::contains));
        assertEquals(2, search.search("people", "MARIA BUSCA", 2, 5, null, null, "guardian", null, true, admin).content().size());
    }
    @Test void statusFiltersBeforePaginationWithOrWithoutText() {
        var admin = actor("ADMIN");
        for (int i = 0; i < 7; i++) {
            var candidate = person("Filtro global " + i, LocalDate.of(1990, 1, 1), PersonTypeCode.GUARDIAN);
            if (i >= 5) candidate.setStatus(RecordStatus.INACTIVE);
        }
        em.flush();
        em.clear();

        var first = search.search("people", "Filtro global", 0, 3, null, "ACTIVE", "", null, false, admin);
        var second = search.search("people", "Filtro global", 1, 3, null, "ACTIVE", "", null, false, admin);
        assertEquals(5, first.totalElements());
        assertEquals(2, first.totalPages());
        assertEquals(3, first.content().size());
        assertEquals(2, second.content().size());
        assertEquals(2, search.search("people", "Filtro global", 0, 20, null, "INACTIVE", "", null, false, admin).totalElements());

        long inactiveCount = em.createQuery("select count(p) from Person p where p.status = :status", Long.class)
                .setParameter("status", RecordStatus.INACTIVE).getSingleResult();
        var statusOnly = search.search("people", "", 0, 2, null, "INACTIVE", "", null, false, admin);
        assertEquals(inactiveCount, statusOnly.totalElements());
        assertTrue(statusOnly.content().stream().map(PersonDtos.Response.class::cast)
                .allMatch(person -> person.status() == RecordStatus.INACTIVE));
        assertEquals(0, search.search("people", "Filtro global", 0, 20, null, "SUSPENDED", "", null, false, admin).totalElements());

        assertThrows(ResponseStatusException.class,
                () -> search.search("people", "", 0, 20, null, "UNKNOWN", "", null, false, admin));
        assertThrows(ResponseStatusException.class,
                () -> search.search("roles", "", 0, 20, null, "ACTIVE", "", null, false, admin));
    }
    @Test void allSearchResourcesCompileBothWithAndWithoutText() {
        var admin = actor("ADMIN");
        for (String resource : List.of("people", "learners", "organizations", "contracts", "cohorts", "lessons", "activities", "notifications", "users", "roles", "document-types")) {
            assertDoesNotThrow(() -> search.search(resource, "test", 0, 20, null, null, "", null, false, admin), resource);
            assertDoesNotThrow(() -> search.search(resource, "", 0, 20, null, null, "", null, false, admin), resource);
        }
        for (String resource : List.of("people", "learners", "organizations", "contracts", "cohorts", "users"))
            assertDoesNotThrow(() -> search.search(resource, "", 0, 5, null, "ACTIVE", "", null, false, admin), resource);
        assertDoesNotThrow(() -> search.search("lessons", "", 0, 5, null, "SCHEDULED", "", null, false, admin));
        assertDoesNotThrow(() -> search.search("activities", "", 0, 5, null, "PUBLISHED", "", null, false, admin));
    }
    @Test void lookupEligibilityAndLiteralWildcardAreAppliedBeforeLimit() {
        var admin = actor("ADMIN");
        long existingAdultGuardians = search.search("people", "responsável", 0, 5, null, null, "guardian", null, true, admin).totalElements();
        for(int i=0;i<6;i++) person("Guardian " + i, LocalDate.now().minusYears(10), PersonTypeCode.GUARDIAN);
        person("Guardian adulto", LocalDate.of(1990,1,1), PersonTypeCode.GUARDIAN);
        person("Literal %_", LocalDate.of(1990,1,1), PersonTypeCode.GUARDIAN);
        assertEquals(1, search.search("people", "guardian adulto", 0, 5, null, null, "guardian", null, true, admin).totalElements());
        assertEquals(existingAdultGuardians + 2, search.search("people", "responsável", 0, 5, null, null, "guardian", null, true, admin).totalElements());
        assertEquals(1, search.search("people", "%_", 0, 20, null, null, "", null, false, admin).totalElements());
        assertEquals(0, search.search("people", "guardian", 0, 5, "INSTRUCTOR", null, "member", null, true, admin).totalElements());
        for (String[] pair : List.of(new String[]{"people","instructor"},new String[]{"learners","contract-learner"},new String[]{"organizations","employer"},new String[]{"organizations","school"},new String[]{"organizations","parent"},new String[]{"contracts","enrollment"},new String[]{"cohorts","lesson-cohort"},new String[]{"users","audience-user"},new String[]{"cohorts","audience-cohort"},new String[]{"lessons","activity"}))
            assertDoesNotThrow(() -> search.search(pair[0], "", 0, 5, null, null, pair[1], null, true, admin));
    }
    @Test void organizationWithBothTypesAppearsInEmployerAndSchoolLookups() {
        var admin = actor("ADMIN");
        var organization = new Organization();
        organization.getOrganizationTypes().add(em.getReference(OrganizationTypeEntry.class, OrganizationType.EMPLOYER));
        organization.getOrganizationTypes().add(em.getReference(OrganizationTypeEntry.class, OrganizationType.SCHOOL));
        organization.setLegalName("Escola Empresa Busca Dupla");
        organization.setTaxId("99887766554433");
        organization.setContactEmail("dupla@search.test");
        organization.setPhoneNumber("41999999999");
        var address = new dev.lourencogabriel.inatead.address.model.Address();
        address.setPostalCode("83200000"); address.setStreet("Rua teste");
        address.setStreetNumber("1"); address.setDistrict("Centro");
        address.setCity("Paranagua"); address.setStateCode("PR");
        organization.setAddress(address);
        em.persist(organization);
        em.flush();

        assertEquals(1, search.search("organizations", "Busca Dupla", 0, 5, null, null, "employer", null, true, admin).totalElements());
        assertEquals(1, search.search("organizations", "Busca Dupla", 0, 5, null, null, "school", null, true, admin).totalElements());
    }
    @Test void nonAdminQueriesKeepTheirScopeAndDoNotExposeAdminCatalogs() {
        var instructor = actor("INSTRUCTOR");
        for (String resource : List.of("learners", "contracts", "lessons", "activities"))
            assertEquals(0, search.search(resource, "", 0, 20, null, null, "", null, false, instructor).totalElements());
        for (String resource : List.of("people", "users", "roles", "cohorts", "notifications"))
            assertThrows(AccessDeniedException.class, () -> search.search(resource, "", 0, 20, null, null, "", null, false, instructor));
        var manager = actor("EMPLOYER_MANAGER");
        for (String resource : List.of("learners", "contracts", "organizations"))
            assertEquals(0, search.search(resource, "", 0, 20, null, null, "", null, false, manager).totalElements());
        assertThrows(AccessDeniedException.class, () -> search.search("learners", "", 0, 5, null, null, "participant", "unknown", true, instructor));
    }

    @Test void lookupControllerRejectsMoreThanFiveAndInvalidPages() throws Exception {
        var controller = new PortalSearchController(search);
        var method = PortalSearchController.class.getMethod("lookup", String.class, String.class, int.class, int.class, String.class, String.class, String.class, Authentication.class);
        var validate = validator.forExecutables();
        assertFalse(validate.validateParameters(controller, method, new Object[]{"people", "", 0, 6, null, "guardian", null, null}).isEmpty());
        assertFalse(validate.validateParameters(controller, method, new Object[]{"people", "", -1, 5, null, "guardian", null, null}).isEmpty());
        assertFalse(validate.validateParameters(controller, method, new Object[]{"people", "a".repeat(161), 0, 5, null, "guardian", null, null}).isEmpty());
        assertTrue(validate.validateParameters(controller, method, new Object[]{"people", "", 0, 5, null, "guardian", null, null}).isEmpty());
    }

    @Test void onlineParticipantLookupFiltersContractsAndExistingParticipantsInDatabase() {
        var admin = actor("ADMIN");
        var instructor = actor("INSTRUCTOR");
        var teacher = em.find(User.class, instructor.getName()).getPerson();
        var cohort = new Cohort();
        cohort.setCode("SEARCH-COHORT"); cohort.setName("Busca turma"); cohort.setDefaultWeekday(4);
        cohort.setShiftCode("MORNING"); cohort.setStartDate(LocalDate.of(2026,1,1));
        em.persist(cohort);
        var lesson = new Lesson(); lesson.setCohort(cohort); lesson.setInstructorPerson(teacher);
        lesson.setDescription("Descrição da aula de teste");
        lesson.setTitle("Aula pesquisável"); lesson.setStartsAt(Instant.parse("2026-09-11T01:00:00Z"));
        lesson.setEndsAt(Instant.parse("2026-09-11T02:00:00Z")); lesson.setDeliveryMode(DeliveryMode.ONLINE);
        em.persist(lesson);
        var employer = new Organization(); employer.getOrganizationTypes().add(em.getReference(OrganizationTypeEntry.class, OrganizationType.EMPLOYER));
        employer.setLegalName("Empresa busca"); employer.setTaxId("12345678901234");
        employer.setContactEmail("org@search.test"); employer.setPhoneNumber("41999999999");
        employer.setAddress(new dev.lourencogabriel.inatead.address.model.Address());
        employer.getAddress().setPostalCode("83200000"); employer.getAddress().setStreet("Rua teste");
        employer.getAddress().setStreetNumber("1"); employer.getAddress().setDistrict("Centro");
        employer.getAddress().setCity("Paranagua"); employer.getAddress().setStateCode("PR");
        em.persist(employer);
        Learner eligible = null;
        for (int i = 0; i < 10; i++) {
            var learner = new Learner(); learner.setPerson(person("Aprendiz busca " + i, LocalDate.of(2000,1,1), PersonTypeCode.LEARNER));
            learner.setHasCompletedHighSchool(true); em.persist(learner);
            var contract = new Contract(); contract.setLearner(learner); contract.setEmployer(employer);
            contract.setMonthlySalary(new BigDecimal("1000")); contract.setStatus(ContractStatus.ACTIVE);
            contract.setStartDate(LocalDate.of(2026,9,10)); contract.setEndDate(LocalDate.of(2026,9,10));
            contract.setWeeklyWorkloadMinutes(i < 7 ? 1200 : 1800); em.persist(contract);
            if (i == 7) { var participant = new LessonParticipant(); participant.setLesson(lesson); participant.setLearner(learner); participant.setParticipationType(ParticipationType.EXTRA); participant.setStatus(ParticipationStatus.EXPECTED); em.persist(participant); }
            if (i == 8) contract.setEndDate(LocalDate.of(2026,9,9));
            if (i == 9) eligible = learner;
        }
        em.flush();
        String registration = String.valueOf(eligible.getRegistrationNumber());
        var result = search.search("learners", registration, 0, 5, null, null, "participant", lesson.getId(), true, admin);
        assertEquals(1, result.totalElements());
        assertEquals(eligible.getId(), ((PortalSearchService.Option) result.content().getFirst()).id());
        assertEquals(0, search.search("learners", registration, 0, 5, null, null, "participant", lesson.getId(), true, instructor).totalElements(), "Instructor must not enumerate learners outside their scope");
        assertEquals(1, search.search("lessons", "pesquisável", 0, 20, null, null, "", null, false, instructor).totalElements());
        var other = actor("INSTRUCTOR");
        assertEquals(0, search.search("lessons", "pesquisável", 0, 20, null, null, "", null, false, other).totalElements());
        assertThrows(AccessDeniedException.class, () -> search.search("learners", "", 0, 5, null, null, "participant", lesson.getId(), true, other));
    }
}
