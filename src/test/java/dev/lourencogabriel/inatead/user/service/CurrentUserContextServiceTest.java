package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.model.OrganizationTypeEntry;
import dev.lourencogabriel.inatead.organization.model.OrganizationMembership;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserContextServiceTest {

    private static final String USER_ID = "01JTESTUSER0000000000000000";
    private static final String PERSON_ID = "01JTESTPERSON000000000000000";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    @Mock
    private UserService userService;

    @Mock
    private LearnerRepository learnerRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    private CurrentUserContextService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC);
        this.service = new CurrentUserContextService(
                this.userService,
                this.learnerRepository,
                this.membershipRepository,
                clock
        );
    }

    @Test
    void shouldReturnLearnerAndOnlyEmployerOrganizationsForManager() {
        Learner learner = new Learner();
        learner.setId("01JTESTLEARNER0000000000000");
        Organization employer = organization("01JTESTEMPLOYER000000000000", OrganizationType.EMPLOYER);
        Organization school = organization("01JTESTSCHOOL00000000000000", OrganizationType.SCHOOL);

        when(this.userService.findById(USER_ID)).thenReturn(user(Set.of("EMPLOYER_MANAGER")));
        when(this.learnerRepository.findByPerson_Id(PERSON_ID)).thenReturn(Optional.of(learner));
        when(this.membershipRepository.findActiveByPersonId(PERSON_ID, TODAY)).thenReturn(List.of(
                membership(employer),
                membership(school),
                membership(employer)
        ));

        var response = this.service.find(USER_ID);

        assertEquals(learner.getId(), response.learnerId());
        assertEquals(List.of(employer.getId()), response.organizationIds());
        verify(this.membershipRepository).findActiveByPersonId(PERSON_ID, TODAY);
    }

    @Test
    void shouldNotExposeMembershipsToAUserWithoutManagerRole() {
        when(this.userService.findById(USER_ID)).thenReturn(user(Set.of("LEARNER")));
        when(this.learnerRepository.findByPerson_Id(PERSON_ID)).thenReturn(Optional.empty());

        var response = this.service.find(USER_ID);

        assertNull(response.learnerId());
        assertEquals(List.of(), response.organizationIds());
        verifyNoInteractions(this.membershipRepository);
    }

    private UserResponse user(Set<String> roles) {
        return new UserResponse(
                USER_ID,
                PERSON_ID,
                "Test User",
                "test@example.com",
                UserAccountStatus.ACTIVE,
                roles,
                null,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private Organization organization(String id, OrganizationType type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.getOrganizationTypes().add(new OrganizationTypeEntry(type));
        return organization;
    }

    private OrganizationMembership membership(Organization organization) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganization(organization);
        return membership;
    }
}
