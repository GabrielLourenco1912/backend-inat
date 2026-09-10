package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.auth.service.AccountSecurityService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.dto.UserDtos;
import dev.lourencogabriel.inatead.user.dto.UserResponse;
import dev.lourencogabriel.inatead.user.exception.PersonAlreadyLinkedToUserException;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.UserRole;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleRepository userRoleRepository;
    private final AccountSecurityService accountSecurityService;

    @Transactional
    public UserResponse create(UserDtos.CreateRequest request) {
        String email = normalizeEmail(request.loginEmail());
        ensureEmailAvailable(email, null);
        Person person = resolvePerson(request.personId());
        ensurePersonIsActive(person);
        validateAdministrativeActivation(request.status(), false);

        User user = new User(request.displayName().trim(), email, passwordEncoder.encode(request.password()));
        user.setPerson(person);
        this.accountSecurityService.applyAdministrativeUpdate(user, request.status(), false);
        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional
    public UserResponse update(String id, UserDtos.UpdateRequest request) {
        User user = requireForUpdate(id);
        if (user.getStatus() == UserAccountStatus.ACTIVE
                && request.status() != UserAccountStatus.ACTIVE
                && hasRole(user, "ADMIN")
                && userRoleRepository.countActiveUsersByRoleCode("ADMIN") <= 1) {
            throw new BusinessRuleException("The last active administrator cannot be disabled");
        }
        String email = normalizeEmail(request.loginEmail());
        ensureEmailAvailable(email, id);
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(email);
        boolean passwordChanged = request.password() != null && !request.password().isBlank();
        validateAdministrativeActivation(
                request.status(),
                !emailChanged && user.getEmailVerifiedAt() != null
        );

        user.setName(request.displayName().trim());
        user.setEmail(email);
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (emailChanged) {
            user.setEmailVerifiedAt(null);
        }
        this.accountSecurityService.applyAdministrativeUpdate(
                user,
                request.status(),
                emailChanged || passwordChanged
        );
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(String id) {
        User user = requireForUpdate(id);
        if (user.getStatus() == UserAccountStatus.ACTIVE
                && hasRole(user, "ADMIN")
                && userRoleRepository.countActiveUsersByRoleCode("ADMIN") <= 1) {
            throw new BusinessRuleException("The last active administrator cannot be deleted");
        }
        userRepository.delete(user);
    }

    private User require(String id) {
        return userRepository.findWithRolesByUlid(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private User requireForUpdate(String id) {
        return userRepository.findWithRolesByUlidForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private void ensureEmailAvailable(String email, String currentId) {
        boolean duplicate = currentId == null
                ? userRepository.existsByEmail(email)
                : userRepository.existsByEmailAndUlidNot(email, currentId);
        if (duplicate) {
            throw new ResourceConflictException("A user with this login email already exists");
        }
    }

    private Person resolvePerson(String personId) {
        if (personId == null || personId.isBlank()) {
            throw new BusinessRuleException("A user must be linked to a person");
        }
        if (userRepository.existsByPerson_Id(personId)) {
            throw new PersonAlreadyLinkedToUserException();
        }
        return EntityLookup.require(personRepository, personId, "Person");
    }

    private void ensurePersonIsActive(Person person) {
        if (person.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("A user account requires an active person");
        }
    }

    private void validateAdministrativeActivation(
            UserAccountStatus requestedStatus,
            boolean emailVerified
    ) {
        if (requestedStatus == UserAccountStatus.ACTIVE && !emailVerified) {
            throw new BusinessRuleException(
                    "An account without a verified email must remain INVITED until verification is complete"
            );
        }
    }

    private UserResponse toResponse(User user) {
        Set<String> roles = user.getUserRoles() == null
                ? Set.of()
                : user.getUserRoles().stream()
                        .map(UserRole::getRole)
                        .filter(java.util.Objects::nonNull)
                        .filter(role -> SystemRoleCode.isSupported(role.getCode()))
                        .map(role -> role.getCode())
                        .collect(Collectors.toUnmodifiableSet());
        return new UserResponse(
                user.getUlid(),
                user.getPerson() == null ? null : user.getPerson().getId(),
                user.getName(),
                user.getEmail(),
                user.getStatus(),
                roles,
                user.getEmailVerifiedAt(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasRole(User user, String roleCode) {
        return user.getUserRoles() != null && user.getUserRoles().stream()
                .map(UserRole::getRole)
                .filter(java.util.Objects::nonNull)
                .anyMatch(role -> roleCode.equals(role.getCode()));
    }
}
