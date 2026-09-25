package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.person.service.PersonTypeRules;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.dto.UserRoleDtos;
import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.UserRole;
import dev.lourencogabriel.inatead.user.model.UserRoleId;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LearnerRepository learnerRepository;
    private final PersonRepository personRepository;

    @Transactional
    public void grantInitialRoles(User user) {
        for (var type : user.getPerson().getPersonTypes()) {
            String code = type.getCode().name();
            if (!SystemRoleCode.isSupported(code)) {
                continue;
            }
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Standard role is missing: " + code));
            grant(user.getUlid(), role.getId(), null);
        }
    }

    @Transactional
    public UserRoleDtos.Response grant(String userId, Short roleId, String grantedByUserId) {
        UserRoleId id = new UserRoleId(userId, roleId);
        if (userRoleRepository.existsById(id)) {
            throw new ResourceConflictException("The user already has this role");
        }

        User user = EntityLookup.require(userRepository, userId, "User");
        Role role = EntityLookup.require(roleRepository, roleId, "Role");
        if (!SystemRoleCode.isSupported(role.getCode())) {
            throw new BusinessRuleException("Only standard system roles can be granted");
        }
        validateRoleRequirements(user, role);

        UserRole assignment = new UserRole();
        assignment.setId(id);
        assignment.setUser(user);
        assignment.setRole(role);
        if (grantedByUserId != null) {
            assignment.setGrantedByUser(EntityLookup.require(
                    userRepository,
                    grantedByUserId,
                    "Granting user"
            ));
        }

        return toResponse(userRoleRepository.save(assignment));
    }

    private void validateRoleRequirements(User user, Role role) {
        Person person = user.getPerson() == null ? null
                : personRepository.findByIdForUpdate(user.getPerson().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Person", user.getPerson().getId()));
        PersonTypeRules.requireType(person, PersonTypeCode.valueOf(role.getCode()));
        if (!SystemRoleCode.LEARNER.name().equals(role.getCode())) {
            return;
        }
        if (user.getPerson() == null
                || learnerRepository.findByPerson_Id(user.getPerson().getId())
                        .filter(learner -> learner.getStatus() == RecordStatus.ACTIVE)
                        .isEmpty()) {
            throw new BusinessRuleException(
                    "The LEARNER role requires an active learner profile linked to the user's person"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<UserRoleDtos.Response> findByUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userRoleRepository.findAllByUser_Ulid(userId).stream()
                .filter(assignment -> assignment.getRole() != null)
                .filter(assignment -> SystemRoleCode.isSupported(
                        assignment.getRole().getCode()
                ))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void revoke(String userId, Short roleId) {
        UserRole assignment = userRoleRepository.findById(new UserRoleId(userId, roleId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User role",
                        userId + "/" + roleId
                ));
        if ("ADMIN".equals(assignment.getRole().getCode())
                && assignment.getUser().getStatus() == UserAccountStatus.ACTIVE
                && userRoleRepository.countActiveUsersByRoleCode("ADMIN") <= 1) {
            throw new BusinessRuleException(
                    "The last active administrator role cannot be revoked"
            );
        }
        userRoleRepository.delete(assignment);
    }

    private UserRoleDtos.Response toResponse(UserRole assignment) {
        return new UserRoleDtos.Response(
                assignment.getUser().getUlid(),
                assignment.getRole().getId(),
                assignment.getRole().getCode(),
                assignment.getRole().getName(),
                assignment.getGrantedByUser() == null
                        ? null
                        : assignment.getGrantedByUser().getUlid(),
                assignment.getGrantedAt()
        );
    }
}
