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
        var first = search.search("people", "MARIA BUSCA", 0, 5, null, "guardian", null, true, admin);
        var second = search.search("people", "MARIA BUSCA", 1, 5, null, "guardian", null, true, admin);
        assertEquals(12, first.totalElements()); assertEquals(3, first.totalPages()); assertEquals(5, first.content().size());
        assertTrue(first.content().stream().noneMatch(second.content()::contains));
        assertEquals(2, search.search("people", "MARIA BUSCA", 2, 5, null, "guardian", null, true, admin).content().size());
    }
    @Test void allSearchResourcesCompileBothWithAndWithoutText() {
        var admin = actor("ADMIN");
        for (String resource : List.of("people", "learners", "organizations", "contracts", "cohorts", "lessons", "activities", "notifications", "users", "roles", "document-types")) {
            assertDoesNotThrow(() -> search.search(resource, "test", 0, 20, null, "", null, false, admin), resource);
            assertDoesNotThrow(() -> search.search(resource, "", 0, 20, null, "", null, false, admin), resource);
        }
    }
    @Test void lookupEligibilityAndLiteralWildcardAreAppliedBeforeLimit() {
        var admin = actor("ADMIN");
        for(int i=0;i<6;i++) person("Guardian " + i, LocalDate.now().minusYears(10), PersonTypeCode.GUARDIAN);
        person("Guardian adulto", LocalDate.of(1990,1,1), PersonTypeCode.GUARDIAN);
        person("Literal %_", LocalDate.of(1990,1,1), PersonTypeCode.GUARDIAN);
        assertEquals(1, search.search("people", "guardian adulto", 0, 5, null, "guardian", null, true, admin).totalElements());
        assertEquals(2, search.search("people", "responsável", 0, 5, null, "guardian", null, true, admin).totalElements());
        assertEquals(1, search.search("people", "%_", 0, 20, null, "", null, false, admin).totalElements());
        assertEquals(0, search.search("people", "guardian", 0, 5, "INSTRUCTOR", "member", null, true, admin).totalElements());
        for (String[] pair : List.of(new String[]{"people","instructor"},new String[]{"learners","contract-learner"},new String[]{"organizations","employer"},new String[]{"organizations","school"},new String[]{"organizations","parent"},new String[]{"contracts","enrollment"},new String[]{"cohorts","lesson-cohort"},new String[]{"users","audience-user"},new String[]{"cohorts","audience-cohort"},new String[]{"lessons","activity"}))
            assertDoesNotThrow(() -> search.search(pair[0], "", 0, 5, null, pair[1], null, true, admin));
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

        assertEquals(1, search.search("organizations", "Busca Dupla", 0, 5, null, "employer", null, true, admin).totalElements());
        assertEquals(1, search.search("organizations", "Busca Dupla", 0, 5, null, "school", null, true, admin).totalElements());
    }
    @Test void nonAdminQueriesKeepTheirScopeAndDoNotExposeAdminCatalogs() {
        var instructor = actor("INSTRUCTOR");
        for (String resource : List.of("learners", "contracts", "lessons", "activities"))
            assertEquals(0, search.search(resource, "", 0, 20, null, "", null, false, instructor).totalElements());
        for (String resource : List.of("people", "users", "roles", "cohorts", "notifications"))
            assertThrows(AccessDeniedException.class, () -> search.search(resource, "", 0, 20, null, "", null, false, instructor));
        var manager = actor("EMPLOYER_MANAGER");
        for (String resource : List.of("learners", "contracts", "organizations"))
            assertEquals(0, search.search(resource, "", 0, 20, null, "", null, false, manager).totalElements());
        assertThrows(AccessDeniedException.class, () -> search.search("learners", "", 0, 5, null, "participant", "unknown", true, instructor));
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
            learner.setRegistrationNumber("REG-" + i); learner.setHasCompletedHighSchool(true); em.persist(learner);
            var contract = new Contract(); contract.setLearner(learner); contract.setEmployer(employer);
            contract.setMonthlySalary(new BigDecimal("1000")); contract.setStatus(ContractStatus.ACTIVE);
            contract.setStartDate(LocalDate.of(2026,9,10)); contract.setEndDate(LocalDate.of(2026,9,10));
            contract.setWeeklyWorkloadMinutes(i < 7 ? 1200 : 1800); em.persist(contract);
            if (i == 7) { var participant = new LessonParticipant(); participant.setLesson(lesson); participant.setLearner(learner); participant.setParticipationType(ParticipationType.EXTRA); participant.setStatus(ParticipationStatus.EXPECTED); em.persist(participant); }
            if (i == 8) contract.setEndDate(LocalDate.of(2026,9,9));
            if (i == 9) eligible = learner;
        }
        em.flush();
        var result = search.search("learners", "REG", 0, 5, null, "participant", lesson.getId(), true, admin);
        assertEquals(1, result.totalElements());
        assertEquals(eligible.getId(), ((PortalSearchService.Option) result.content().getFirst()).id());
        assertEquals(0, search.search("learners", "REG", 0, 5, null, "participant", lesson.getId(), true, instructor).totalElements(), "Instructor must not enumerate learners outside their scope");
        assertEquals(1, search.search("lessons", "pesquisável", 0, 20, null, "", null, false, instructor).totalElements());
        var other = actor("INSTRUCTOR");
        assertEquals(0, search.search("lessons", "pesquisável", 0, 20, null, "", null, false, other).totalElements());
        assertThrows(AccessDeniedException.class, () -> search.search("learners", "", 0, 5, null, "participant", lesson.getId(), true, other));
    }
}
