package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.user.dto.RoleDtos;
import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> STANDARD_ROLE_CODES = SystemRoleCode.supportedCodes();

    private final RoleRepository repository;

    @Transactional
    public RoleDtos.Response create(RoleDtos.Request request) {
        Role role = new Role();
        apply(role, request, true);
        return toResponse(repository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleDtos.Response findById(Short id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(repository.findAllByCodeIn(
                STANDARD_ROLE_CODES,
                pageable
        ).map(this::toResponse));
    }

    @Transactional
    public RoleDtos.Response update(Short id, RoleDtos.Request request) {
        Role role = require(id);
        String requestedCode = request.code().trim().toUpperCase(Locale.ROOT);
        if (STANDARD_ROLE_CODES.contains(role.getCode())
                && !role.getCode().equals(requestedCode)) {
            throw new BusinessRuleException("A standard role code cannot be changed");
        }
        apply(role, request, false);
        return toResponse(repository.save(role));
    }

    @Transactional
    public void delete(Short id) {
        Role role = require(id);
        if (STANDARD_ROLE_CODES.contains(role.getCode())) {
            throw new BusinessRuleException("Standard roles cannot be deleted");
        }
        repository.delete(role);
    }

    private void apply(Role role, RoleDtos.Request request, boolean creating) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!SystemRoleCode.isSupported(code)) {
            throw new BusinessRuleException(
                    "Role code must be one of: " + String.join(", ", STANDARD_ROLE_CODES)
            );
        }
        boolean duplicate = creating
                ? repository.existsByCode(code)
                : repository.existsByCodeAndIdNot(code, role.getId());
        if (duplicate) {
            throw new ResourceConflictException("A role with this code already exists");
        }
        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(normalize(request.description()));
    }

    private Role require(Short id) {
        return repository.findById(id)
                .filter(role -> SystemRoleCode.isSupported(role.getCode()))
                .orElseThrow(() -> new ResourceNotFoundException("Role", String.valueOf(id)));
    }

    private RoleDtos.Response toResponse(Role role) {
        return new RoleDtos.Response(role.getId(), role.getCode(), role.getName(), role.getDescription());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
