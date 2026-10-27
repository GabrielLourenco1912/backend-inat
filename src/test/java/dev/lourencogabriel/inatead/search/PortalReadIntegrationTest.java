package dev.lourencogabriel.inatead.search;

import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.user.service.UserRoleService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:portal_reads_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;INIT=SET TIME ZONE 'UTC'")
@ActiveProfiles("test")
@Transactional
class PortalReadIntegrationTest {
    @Autowired PortalReadService reads;
    @Autowired PersonService people;
    @Autowired RoleRepository roles;
    @Autowired UserRoleService grants;
    @Autowired EntityManager em;
    @Autowired org.springframework.web.context.WebApplicationContext web;
    static long sequence=87000000000L;
    private Person person(String name,PersonTypeCode type) {
        var response=people.create(new PersonDtos.Request(new AddressDtos.Request("83200000","Rua teste","10",null,"Centro","Paranagua","PR","BR"),name,String.valueOf(++sequence),null,"41999999999",LocalDate.of(1990,1,1),null,RecordStatus.ACTIVE,Set.of(type)));
        return em.find(Person.class,response.id());
    }
    private Authentication actor(String role) {
        var user=new User("Pagination "+role,(++sequence)+"@pagination.test","hash"); user.setPerson(person("Actor",PersonTypeCode.valueOf(role))); em.persist(user);em.flush();
        grants.grant(user.getUlid(),roles.findAll().stream().filter(r->r.getCode().equals(role)).findFirst().orElseThrow().getId(),null); em.flush();em.clear();
        return new UsernamePasswordAuthenticationToken(user.getUlid(),"n/a",List.of());
    }
    @Test void everyReadModelCompilesAndIsBounded() {
        var auth=actor("ADMIN");
        for(var resource:List.of("people","learners","organizations","contracts","cohorts","lessons","activities","cohort-enrollments","organization-memberships","learner-guardians","lesson-participants","lesson-attendance","learner-lessons","activity-submissions","person-documents","contract-documents","users","roles","document-types","notifications","notification-recipients","status-history","school-history","document-history")) {
            var page=assertDoesNotThrow(()->reads.page(resource,Map.of("size","2"),auth),resource);
            assertTrue(page.content().size()<=2,resource);
            assertDoesNotThrow(()->reads.page(resource,Map.of("size","2","q","remote"),auth),resource+" search");
        }
        assertDoesNotThrow(()->reads.dashboard(auth));
    }
    @Test void searchAndCountPrecedeLimitAndReturnNoHistories() {
        var auth=actor("ADMIN");
        for(int i=0;i<25;i++) person("Unrelated "+i,PersonTypeCode.GUARDIAN);
        for(int i=0;i<7;i++) person("Remote matching "+i,PersonTypeCode.GUARDIAN);
        em.flush();
        var first=reads.page("people",Map.of("q","Remote matching","size","5"),auth);
        var second=reads.page("people",Map.of("q","Remote matching","size","5","page","1"),auth);
        assertEquals(7,first.totalElements());assertEquals(5,first.content().size());assertEquals(2,second.content().size());
        assertTrue(first.content().stream().noneMatch(second.content()::contains));
        assertNotNull(first.content().getFirst().get("fullName"));assertNotNull(first.content().getFirst().get("address"));assertEquals(List.of("GUARDIAN"),first.content().getFirst().get("personTypes"));
        assertFalse(reads.page("contracts",Map.of("size","1"),auth).content().stream().anyMatch(row->row.containsKey("statusHistory")||row.containsKey("schoolHistory")));
    }
    @Test void invalidFiltersAreRejectedAndAnInstructorCanOpenAnEmptySearch() {
        var auth=actor("INSTRUCTOR");
        assertEquals(0,reads.page("learners",Map.of(),auth).totalElements());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->reads.page("users",Map.of(),auth));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->reads.page("lessons",Map.of("size","101"),auth));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->reads.page("lessons",Map.of("status","WRONG"),auth));
    }

    @Test void summaryRoutesKeepTheEnvelopeAndLegacyDetailRepresentation() throws Exception {
        var auth=actor("ADMIN");
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/search/people").principal(auth).param("view","summary").param("q","Actor").param("size","1"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.size").value(1))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.content[0].fullName").value("Actor"));
            var id=reads.page("contracts",Map.of("size","1"),auth).content().getFirst().get("id").toString();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/contracts/"+id).principal(auth).param("view","summary"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.statusHistory").doesNotExist());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/contracts/"+id).principal(auth))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.statusHistory").isArray());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/lookups/document-types").principal(auth))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.size").value(5));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void fillPreviewAndContextFiltersDoNotChangeTheCurrentPageOrItsTotal() {
        var auth=actor("ADMIN");
        var page=reads.page("lesson-attendance",Map.of("lessonId","missing","previewFill","{\"q\":\"other filter\",\"status\":\"EXPECTED\"}"),auth);
        assertEquals(0,page.totalElements());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->reads.page("lesson-attendance",Map.of("lessonId","missing","previewFill","bad-json"),auth));
    }

    @Test void guardianRelationshipFiltersAreAppliedBeforeCountAndPagination() {
        var auth=actor("ADMIN");
        var learner=new dev.lourencogabriel.inatead.learner.model.Learner();learner.setPerson(person("Guardian scope learner",PersonTypeCode.LEARNER));em.persist(learner);
        for(int i=0;i<3;i++) {
            var guardian=new dev.lourencogabriel.inatead.learner.model.LearnerGuardian();guardian.setLearner(learner);guardian.setGuardianPerson(person("Remote guardian "+i,PersonTypeCode.GUARDIAN));guardian.setRelationshipType("FAMILY");guardian.setLegalGuardian(i<2);guardian.setPrimaryContact(i==0);em.persist(guardian);
        }
        em.flush();
        var first=reads.page("learner-guardians",Map.of("learnerId",learner.getId(),"q","Remote guardian","status","LEGAL","size","1"),auth);
        assertEquals(2,first.totalElements());assertEquals(1,first.content().size());
        assertEquals("Paranagua",first.content().getFirst().get("city"));
        assertEquals(1,reads.page("learner-guardians",Map.of("learnerId",learner.getId(),"status","PRIMARY"),auth).totalElements());
        assertEquals(1,reads.page("learner-guardians",Map.of("learnerId",learner.getId(),"status","OTHER"),auth).totalElements());
    }

    @Test void instructorAndLearnerScopesApplyBeforeEmptySearchAndCounts() {
        var instructorAuth=actor("INSTRUCTOR");
        var instructor=em.find(User.class,instructorAuth.getName());
        var learner=new dev.lourencogabriel.inatead.learner.model.Learner();learner.setPerson(person("Scoped learner",PersonTypeCode.LEARNER));em.persist(learner);
        var cohort=em.createQuery("select c from Cohort c",dev.lourencogabriel.inatead.cohort.model.Cohort.class).setMaxResults(1).getSingleResult();
        var lesson=new dev.lourencogabriel.inatead.lesson.model.Lesson();lesson.setCohort(cohort);lesson.setInstructorPerson(instructor.getPerson());lesson.setTitle("Scoped lesson");lesson.setDescription("Scope");lesson.setStartsAt(java.time.Instant.parse("2026-10-26T13:00:00Z"));lesson.setEndsAt(java.time.Instant.parse("2026-10-26T15:00:00Z"));lesson.setDeliveryMode(dev.lourencogabriel.inatead.shared.model.DeliveryMode.ONSITE);em.persist(lesson);
        var participant=new dev.lourencogabriel.inatead.lesson.model.LessonParticipant();participant.setLesson(lesson);participant.setLearner(learner);em.persist(participant);
        for(var state:List.of(dev.lourencogabriel.inatead.shared.model.ActivityStatus.DRAFT,dev.lourencogabriel.inatead.shared.model.ActivityStatus.PUBLISHED)) {
            var activity=new dev.lourencogabriel.inatead.activity.model.Activity();activity.setLesson(lesson);activity.setCreatedByUser(instructor);activity.setTitle(state.name());activity.setDescription("Scope");activity.setAvailableAt(lesson.getStartsAt());activity.setDueAt(lesson.getEndsAt());activity.setStatus(state);em.persist(activity);
        }
        em.flush();
        assertEquals(1,reads.page("learners",Map.of(),instructorAuth).totalElements());
        assertEquals(learner.getId(),reads.page("learners",Map.of(),instructorAuth).content().getFirst().get("id"));
        assertEquals(2,reads.page("activities",Map.of("lessonId",lesson.getId()),instructorAuth).totalElements());
        var user=new User("Scoped learner",(++sequence)+"@pagination.test","hash");user.setPerson(learner.getPerson());em.persist(user);em.flush();
        grants.grant(user.getUlid(),roles.findAll().stream().filter(role->role.getCode().equals("LEARNER")).findFirst().orElseThrow().getId(),null);em.flush();
        var learnerAuth=new UsernamePasswordAuthenticationToken(user.getUlid(),"n/a",List.of());
        assertEquals(1,reads.page("learners",Map.of(),learnerAuth).totalElements());
        var activities=reads.page("activities",Map.of("lessonId",lesson.getId()),learnerAuth);
        assertEquals(1,activities.totalElements());assertEquals("PUBLISHED",activities.content().getFirst().get("status"));
        assertEquals(0,reads.page("lessons",Map.of("id","another-lesson"),learnerAuth).totalElements());
    }

    @Test void managerCountsAndPagesHaveTheSameOrganizationScope() {
        var auth=actor("EMPLOYER_MANAGER");var user=em.find(User.class,auth.getName());
        var contract=em.createQuery("select c from Contract c",dev.lourencogabriel.inatead.contract.model.Contract.class).setMaxResults(1).getSingleResult();
        var membership=new dev.lourencogabriel.inatead.organization.model.OrganizationMembership();membership.setPerson(user.getPerson());membership.setOrganization(contract.getEmployer());membership.setMembershipRole("EMPLOYER_MANAGER");membership.setStartDate(LocalDate.of(2020,1,1));em.persist(membership);em.flush();
        String id=contract.getEmployer().getId();
        var learners=reads.page("learners",Map.of("organizationId",id,"size","1"),auth);
        var contracts=reads.page("contracts",Map.of("organizationId",id,"size","1"),auth);
        assertTrue(learners.totalElements()>0);assertEquals(1,learners.content().size());
        var summary=reads.organizationSummary(id,auth);assertEquals(learners.totalElements(),summary.get("learnerCount"));assertEquals(contracts.totalElements(),summary.get("contractCount"));
        assertEquals(1,reads.page("organizations",Map.of(),auth).totalElements());
        assertEquals(0,reads.page("learners",Map.of("organizationId","another-organization"),auth).totalElements());
    }

    @Test void calendarDateFiltersUseSaoPauloDayAndReturnUtcInstants() {
        var auth=actor("ADMIN");var instructor=person("Calendar instructor",PersonTypeCode.INSTRUCTOR);
        var cohort=em.createQuery("select c from Cohort c",dev.lourencogabriel.inatead.cohort.model.Cohort.class).setMaxResults(1).getSingleResult();
        for(String time:List.of("2026-10-27T02:59:59Z","2026-10-27T03:00:00Z","2026-10-28T03:00:00Z")) {
            var lesson=new dev.lourencogabriel.inatead.lesson.model.Lesson();lesson.setCohort(cohort);lesson.setInstructorPerson(instructor);lesson.setTitle("Calendar boundary");lesson.setDescription("Calendar");lesson.setStartsAt(java.time.Instant.parse(time));lesson.setEndsAt(lesson.getStartsAt().plusSeconds(3600));lesson.setDeliveryMode(dev.lourencogabriel.inatead.shared.model.DeliveryMode.ONSITE);em.persist(lesson);
        }
        em.flush();
        var page=reads.page("lessons",Map.of("cohortId",cohort.getId(),"startDate","2026-10-27","endDate","2026-10-27"),auth);
        assertEquals(1,page.totalElements());assertEquals(java.time.Instant.parse("2026-10-27T03:00:00Z"),page.content().getFirst().get("startsAt"));
    }
}
