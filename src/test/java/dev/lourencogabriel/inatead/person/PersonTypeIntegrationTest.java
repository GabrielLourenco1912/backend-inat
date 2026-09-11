package dev.lourencogabriel.inatead.person;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.learner.dto.LearnerDtos;
import dev.lourencogabriel.inatead.learner.dto.LearnerGuardianDtos;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.learner.service.LearnerGuardianService;
import dev.lourencogabriel.inatead.learner.service.LearnerService;
import dev.lourencogabriel.inatead.person.controller.PersonController;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.person.repository.PersonTypeRepository;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.person.service.PersonTypeCatalogInitializer;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.UserRole;
import dev.lourencogabriel.inatead.user.model.UserRoleId;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.user.service.UserRoleService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:person_types_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class PersonTypeIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(700000);

    @Autowired private PersonService people;
    @Autowired private PersonController controller;
    @Autowired private PersonRepository personRepository;
    @Autowired private PersonTypeRepository typeRepository;
    @Autowired private PersonTypeCatalogInitializer initializer;
    @Autowired private LearnerService learners;
    @Autowired private LearnerGuardianService guardians;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private UserRoleService userRoles;
    @Autowired private EntityManager entityManager;

    @Test
    void shouldPersistManyTypesPerPersonAndFilterBeforePagination() {
        var first = create(PersonTypeCode.INSTRUCTOR, PersonTypeCode.GUARDIAN);
        var second = create(PersonTypeCode.GUARDIAN);
        create(PersonTypeCode.ADMIN);
        entityManager.flush();
        entityManager.clear();

        assertEquals(Set.of(PersonTypeCode.INSTRUCTOR, PersonTypeCode.GUARDIAN), people.findById(first.id()).personTypes());
        var page = people.findByType(PersonTypeCode.GUARDIAN, PageRequest.of(0, 1));
        assertEquals(2, page.totalElements());
        assertEquals(1, page.content().size());
        assertTrue(Set.of(first.id(), second.id()).contains(page.content().getFirst().id()));
        assertEquals(5, typeRepository.count());
    }

    @Test
    void shouldPreserveOmittedTypesAndAllowReplacingOrClearingUnusedTypes() {
        var person = create(PersonTypeCode.GUARDIAN);
        assertEquals(person.personTypes(), people.update(person.id(), request(person.taxId(), null)).personTypes());
        assertEquals(Set.of(PersonTypeCode.ADMIN, PersonTypeCode.INSTRUCTOR), people.update(person.id(),
                request(person.taxId(), Set.of(PersonTypeCode.ADMIN, PersonTypeCode.INSTRUCTOR))).personTypes());
        assertTrue(people.update(person.id(), request(person.taxId(), Set.of())).personTypes().isEmpty());
    }

    @Test
    void shouldNotRemoveATypeRequiredByAnAssignedUserRole() {
        var person = create(PersonTypeCode.INSTRUCTOR, PersonTypeCode.GUARDIAN);
        User user = user(person.id());
        short roleId = role("INSTRUCTOR");
        userRoles.grant(user.getUlid(), roleId, null);

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> people.update(person.id(),
                request(person.taxId(), Set.of(PersonTypeCode.GUARDIAN))));
        assertTrue(error.getMessage().contains("assigned user role"));
    }

    @Test
    void shouldAllowRemovingATypeAfterTheRoleIsRevoked() {
        var person = create(PersonTypeCode.INSTRUCTOR, PersonTypeCode.GUARDIAN);
        User user = user(person.id());
        short roleId = role("INSTRUCTOR");
        userRoles.grant(user.getUlid(), roleId, null);
        userRoles.revoke(user.getUlid(), roleId);

        assertEquals(Set.of(PersonTypeCode.GUARDIAN), people.update(person.id(),
                request(person.taxId(), Set.of(PersonTypeCode.GUARDIAN))).personTypes());
    }

    @Test
    void shouldRejectLearnerProfileWithoutLearnerType() {
        var person = create(PersonTypeCode.GUARDIAN);
        assertThrows(BusinessRuleException.class, () -> learners.create(
                new LearnerDtos.Request(person.id(), "WRONG-TYPE", false, RecordStatus.ACTIVE)));
    }

    @Test
    void shouldRejectAnAdultGuardianWithoutGuardianType() {
        var learnerPerson = create(PersonTypeCode.LEARNER);
        var learner = learners.create(new LearnerDtos.Request(learnerPerson.id(), "GUARDIAN-TYPE", false, RecordStatus.ACTIVE));
        var adult = create(PersonTypeCode.INSTRUCTOR);

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> guardians.create(learner.id(),
                new LearnerGuardianDtos.Request(adult.id(), "MOTHER", true, true)));
        assertEquals("The person must have the GUARDIAN person type", error.getMessage());
    }

    @Test
    void shouldAcceptTypedGuardianAndPreventRemovingTheirType() {
        var learnerPerson = create(PersonTypeCode.LEARNER);
        var learner = learners.create(new LearnerDtos.Request(learnerPerson.id(), "TYPED-GUARDIAN", false, RecordStatus.ACTIVE));
        var guardian = create(PersonTypeCode.GUARDIAN);
        assertEquals(guardian.id(), guardians.create(learner.id(),
                new LearnerGuardianDtos.Request(guardian.id(), "MOTHER", true, true)).guardianPersonId());

        assertThrows(BusinessRuleException.class, () -> people.update(guardian.id(), request(guardian.taxId(), Set.of())));
    }

    @Test
    void shouldRequireAdminRoleToEditTypesEvenForPersonWithAdminType() {
        var adminPerson = create(PersonTypeCode.ADMIN);
        User user = user(adminPerson.id());
        var target = create(PersonTypeCode.GUARDIAN);
        entityManager.flush();
        entityManager.clear();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.getUlid(), null, List.of()));
        try {
            assertThrows(AccessDeniedException.class, () -> controller.update(target.id(),
                    request(target.taxId(), Set.of(PersonTypeCode.INSTRUCTOR))));
            userRoles.grant(user.getUlid(), role("ADMIN"), null);
            entityManager.flush();
            entityManager.clear();
            assertDoesNotThrow(() -> controller.update(target.id(), request(target.taxId(), Set.of(PersonTypeCode.INSTRUCTOR))));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldClassifyLegacyRolesAndProfilesOnlyOnFirstInitialization() {
        var person = create();
        User user = user(person.id());
        var role = roleRepository.findById(role("ADMIN")).orElseThrow();
        UserRole assignment = new UserRole();
        assignment.setId(new UserRoleId(user.getUlid(), role.getId()));
        assignment.setUser(user);
        assignment.setRole(role);
        userRoleRepository.save(assignment);
        Learner learner = new Learner();
        learner.setPerson(personRepository.findById(person.id()).orElseThrow());
        learner.setRegistrationNumber("LEGACY-TYPES");
        learnerRepository.save(learner);
        entityManager.flush();
        typeRepository.deleteAllInBatch();
        entityManager.clear();

        initializer.run(null);
        entityManager.clear();
        assertEquals(Set.of(PersonTypeCode.ADMIN, PersonTypeCode.LEARNER), people.findById(person.id()).personTypes());
        initializer.run(null);
        assertEquals(5, typeRepository.count());
        assertEquals(2, personRepository.findById(person.id()).orElseThrow().getPersonTypes().size());
    }

    private PersonDtos.Response create(PersonTypeCode... types) {
        return people.create(request(String.format("%011d", SEQUENCE.incrementAndGet()), Set.of(types)));
    }

    private PersonDtos.Request request(String taxId, Set<PersonTypeCode> types) {
        return new PersonDtos.Request(new AddressDtos.Request("83200000", "Test Street", "10", null,
                "Centro", "Paranaguá", "PR", "BR"), "Person types test", taxId, null,
                "41999999999", LocalDate.of(1990, 1, 1), null, RecordStatus.ACTIVE, types);
    }

    private User user(String personId) {
        User user = new User("Person types user", SEQUENCE.incrementAndGet() + "@types.test", "hash");
        user.setPerson(personRepository.findById(personId).orElseThrow());
        return userRepository.save(user);
    }

    private short role(String code) {
        return roleRepository.findAll().stream().filter(role -> role.getCode().equals(code)).findFirst().orElseThrow().getId();
    }
}
