package dev.lourencogabriel.inatead.auth.authorization;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.UserRole;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DomainAuthorizationTest {

    @Mock private UserRepository userRepository;
    @Mock private LearnerRepository learnerRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private LessonParticipantRepository participantRepository;
    @Mock private AttendanceRecordRepository attendanceRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ActivitySubmissionRepository submissionRepository;
    @Mock private NotificationRecipientRepository notificationRecipientRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMembershipRepository organizationMembershipRepository;
    @Mock private ContractRepository contractRepository;
    @InjectMocks
    private DomainAuthorization authorization;

    private User manager;
    private Authentication authentication;
    private Organization employer;

    @BeforeEach
    void setUp() {
        Person person = new Person();
        person.setId("person");
        Role role = new Role();
        role.setCode("EMPLOYER_MANAGER");
        UserRole assignment = new UserRole();
        assignment.setRole(role);

        manager = new User("Manager", "manager@example.com", "hash");
        manager.setUlid("manager");
        manager.setPerson(person);
        manager.setUserRoles(Set.of(assignment));
        authentication = new UsernamePasswordAuthenticationToken(
                manager,
                null,
                manager.getAuthorities()
        );

        employer = new Organization();
        employer.setId("employer");
        employer.setOrganizationType(OrganizationType.EMPLOYER);
        when(userRepository.findWithRolesByUlid("manager")).thenReturn(Optional.of(manager));
        lenient().when(organizationRepository.findById("employer"))
                .thenReturn(Optional.of(employer));
    }

    @Test
    void shouldAllowManagerOnlyWhenOrganizationMembershipIsActive() {
        when(organizationMembershipRepository.existsActiveMembership(
                "person",
                "employer",
                LocalDate.now()
        )).thenReturn(true);

        assertTrue(authorization.canAccessOrganization(authentication, "employer"));
    }

    @Test
    void shouldRejectManagerWithoutActiveOrganizationMembership() {
        when(organizationMembershipRepository.existsActiveMembership(
                "person",
                "employer",
                LocalDate.now()
        )).thenReturn(false);

        assertFalse(authorization.canAccessOrganization(authentication, "employer"));
    }

    @Test
    void shouldRejectCompanyManagerForASchoolEvenWithAnActiveMembership() {
        employer.setOrganizationType(OrganizationType.SCHOOL);

        assertFalse(authorization.canAccessOrganization(authentication, "employer"));
    }

    @Test
    void shouldHideDraftActivityFromLearnerButExposePublishedActivity() {
        manager.getUserRoles().iterator().next().getRole().setCode("LEARNER");
        authentication = new UsernamePasswordAuthenticationToken(
                manager,
                null,
                manager.getAuthorities()
        );

        Person instructor = new Person();
        instructor.setId("instructor");
        Lesson lesson = new Lesson();
        lesson.setId("lesson");
        lesson.setInstructorPerson(instructor);

        Learner learner = new Learner();
        learner.setId("learner");
        learner.setPerson(manager.getPerson());

        Activity draft = activity("draft", lesson, ActivityStatus.DRAFT);
        Activity published = activity("published", lesson, ActivityStatus.PUBLISHED);
        when(activityRepository.findById("draft")).thenReturn(Optional.of(draft));
        when(activityRepository.findById("published")).thenReturn(Optional.of(published));
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        when(learnerRepository.findByPerson_Id("person")).thenReturn(Optional.of(learner));
        when(participantRepository.existsByLesson_IdAndLearner_Id("lesson", "learner"))
                .thenReturn(true);

        assertFalse(authorization.canAccessActivity(authentication, "draft"));
        assertTrue(authorization.canAccessActivity(authentication, "published"));
    }

    private Activity activity(String id, Lesson lesson, ActivityStatus status) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setLesson(lesson);
        activity.setStatus(status);
        return activity;
    }
}
