package dev.lourencogabriel.inatead.auth.authorization;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.UserRole;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.time.LocalDate;

@Component("domainAuthorization")
@RequiredArgsConstructor
public class DomainAuthorization {

    private final UserRepository userRepository;
    private final LearnerRepository learnerRepository;
    private final LessonRepository lessonRepository;
    private final LessonParticipantRepository participantRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final ActivityRepository activityRepository;
    private final ActivitySubmissionRepository submissionRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final ContractRepository contractRepository;

    @Transactional(readOnly = true)
    public boolean hasAnyRole(Authentication authentication, String... acceptedRoles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Set<String> accepted = Arrays.stream(acceptedRoles)
                .filter(SystemRoleCode::isSupported)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (accepted.isEmpty()) {
            return false;
        }
        User user = currentUser(authentication);
        return user != null && roleCodes(user).stream().anyMatch(accepted::contains);
    }

    public boolean isSelfOrHasAnyRole(
            Authentication authentication,
            String userId,
            String... acceptedRoles
    ) {
        User user = currentUser(authentication);
        if (user == null) {
            return false;
        }
        if (userId != null && userId.equals(user.getUlid())) {
            return true;
        }
        Set<String> accepted = Arrays.stream(acceptedRoles)
                .filter(SystemRoleCode::isSupported)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return roleCodes(user).stream().anyMatch(accepted::contains);
    }

    @Transactional(readOnly = true)
    public boolean canAccessLearner(Authentication authentication, String learnerId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        if (user == null || user.getPerson() == null) {
            return false;
        }
        boolean ownsLearner = learnerRepository.findById(learnerId)
                .map(learner -> learner.getPerson().getId().equals(user.getPerson().getId()))
                .orElse(false);
        if (ownsLearner) {
            return true;
        }
        boolean assignedInstructor = hasAnyRole(authentication, "INSTRUCTOR")
                && participantRepository.existsByLearner_IdAndLesson_InstructorPerson_Id(
                        learnerId,
                        user.getPerson().getId()
                );
        if (assignedInstructor) {
            return true;
        }
        return contractRepository.findAllByLearner_IdOrderByStartDateDesc(learnerId).stream()
                .anyMatch(contract -> isManagerOf(user, contract.getEmployer()));
    }

    @Transactional(readOnly = true)
    public boolean canAccessOwnLearnerData(Authentication authentication, String learnerId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        return user != null
                && user.getPerson() != null
                && learnerRepository.findById(learnerId)
                        .map(learner -> learner.getPerson().getId()
                                .equals(user.getPerson().getId()))
                        .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessOrganization(Authentication authentication, String organizationId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        if (user == null || user.getPerson() == null) {
            return false;
        }
        return organizationRepository.findById(organizationId)
                .map(organization -> isManagerOf(user, organization))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessContract(Authentication authentication, String contractId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        if (user == null || user.getPerson() == null) {
            return false;
        }
        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null) {
            return false;
        }
        if (contract.getLearner().getPerson().getId().equals(user.getPerson().getId())) {
            return true;
        }
        return isManagerOf(user, contract.getEmployer());
    }

    @Transactional(readOnly = true)
    public boolean canAccessLesson(Authentication authentication, String lessonId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        if (user == null || user.getPerson() == null) {
            return false;
        }
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        if (lesson == null) {
            return false;
        }
        if (hasAnyRole(authentication, "INSTRUCTOR")
                && lesson.getInstructorPerson().getId().equals(user.getPerson().getId())) {
            return true;
        }
        return learnerRepository.findByPerson_Id(user.getPerson().getId())
                .map(learner -> participantRepository.existsByLesson_IdAndLearner_Id(
                        lessonId,
                        learner.getId()
                ))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageLesson(Authentication authentication, String lessonId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        return user != null
                && user.getPerson() != null
                && hasAnyRole(authentication, "INSTRUCTOR")
                && lessonRepository.findById(lessonId)
                        .map(lesson -> lesson.getInstructorPerson().getId()
                                .equals(user.getPerson().getId()))
                        .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessParticipant(Authentication authentication, String participantId) {
        LessonParticipant participant = participantRepository.findById(participantId).orElse(null);
        if (participant == null) {
            return false;
        }
        if (canManageLesson(authentication, participant.getLesson().getId())) {
            return true;
        }
        User user = currentUser(authentication);
        return user != null
                && user.getPerson() != null
                && participant.getLearner().getPerson().getId().equals(user.getPerson().getId());
    }

    @Transactional(readOnly = true)
    public boolean canManageParticipant(Authentication authentication, String participantId) {
        return participantRepository.findById(participantId)
                .map(participant -> canManageLesson(
                        authentication,
                        participant.getLesson().getId()
                ))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessAttendance(Authentication authentication, String attendanceId) {
        return attendanceRepository.findById(attendanceId)
                .map(record -> canAccessParticipant(
                        authentication,
                        record.getLessonParticipant().getId()
                ))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageAttendance(Authentication authentication, String attendanceId) {
        return attendanceRepository.findById(attendanceId)
                .map(record -> canManageParticipant(
                        authentication,
                        record.getLessonParticipant().getId()
                ))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessActivity(Authentication authentication, String activityId) {
        return activityRepository.findById(activityId)
                .map(activity -> canManageLesson(authentication, activity.getLesson().getId())
                        || (activity.getStatus() != ActivityStatus.DRAFT
                        && canAccessLesson(authentication, activity.getLesson().getId())))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageActivity(Authentication authentication, String activityId) {
        return activityRepository.findById(activityId)
                .map(activity -> canManageLesson(authentication, activity.getLesson().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canCreateSubmission(
            Authentication authentication,
            String activityId,
            String learnerId
    ) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        if (user == null || user.getPerson() == null) {
            return false;
        }
        return learnerRepository.findById(learnerId)
                .map(learner -> learner.getPerson().getId().equals(user.getPerson().getId())
                        && canAccessActivity(authentication, activityId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessSubmission(Authentication authentication, String submissionId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        return submissionRepository.findById(submissionId)
                .map(submission -> (user != null
                        && user.getPerson() != null
                        && submission.getLearner().getPerson().getId()
                                .equals(user.getPerson().getId()))
                        || canManageActivity(authentication, submission.getActivity().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canEditSubmission(Authentication authentication, String submissionId) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        User user = currentUser(authentication);
        return submissionRepository.findById(submissionId)
                .map(submission -> user != null
                        && user.getPerson() != null
                        && submission.getLearner().getPerson().getId()
                                .equals(user.getPerson().getId())
                        && (submission.getStatus() == SubmissionStatus.DRAFT
                        || submission.getStatus() == SubmissionStatus.RETURNED))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canGradeSubmission(Authentication authentication, String submissionId) {
        return submissionRepository.findById(submissionId)
                .map(submission -> canManageActivity(
                        authentication,
                        submission.getActivity().getId()
                ))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessNotificationRecipient(
            Authentication authentication,
            String recipientId
    ) {
        if (hasAnyRole(authentication, "ADMIN")) {
            return true;
        }
        String currentUserId = userId(authentication);
        return currentUserId != null
                && notificationRecipientRepository
                        .findByIdAndRecipientUser_Ulid(recipientId, currentUserId)
                        .isPresent();
    }

    public String userId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        if (principal instanceof User user) {
            return user.getUlid();
        }
        return authentication.getName();
    }

    @Transactional(readOnly = true)
    public String personId(Authentication authentication) {
        User user = currentUser(authentication);
        return user == null || user.getPerson() == null ? null : user.getPerson().getId();
    }

    private Set<String> roleCodes(User user) {
        if (user.getUserRoles() == null || user.getUserRoles().isEmpty()) {
            return Set.of();
        }
        return user.getUserRoles().stream()
                .map(UserRole::getRole)
                .filter(java.util.Objects::nonNull)
                .map(role -> role.getCode())
                .filter(SystemRoleCode::isSupported)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean isManagerOf(User user, Organization organization) {
        if (organization == null || user.getPerson() == null) {
            return false;
        }
        return organization.hasType(OrganizationType.EMPLOYER)
                && roleCodes(user).contains("EMPLOYER_MANAGER")
                && organizationMembershipRepository.existsActiveMembership(
                        user.getPerson().getId(),
                        organization.getId(),
                        LocalDate.now(BusinessTime.ZONE)
                );
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String id = userId(authentication);
        if (id == null) {
            return null;
        }
        return userRepository.findWithRolesByUlid(id)
                .filter(user -> user.getStatus() == UserAccountStatus.ACTIVE)
                .orElse(null);
    }
}
