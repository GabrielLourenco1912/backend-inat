package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.user.dto.RoleDtos;
import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository repository;

    @InjectMocks
    private RoleService service;

    @Test
    void shouldRejectARequestForACustomRole() {
        RoleDtos.Request request = new RoleDtos.Request(
                "CONTENT_REVIEWER",
                "Content reviewer",
                null
        );

        assertThrows(BusinessRuleException.class, () -> service.create(request));

        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldListOnlyTheFourSupportedRoles() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.findAllByCodeIn(SystemRoleCode.supportedCodes(), pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        assertEquals(0, service.findAll(pageable).totalElements());

        verify(repository).findAllByCodeIn(SystemRoleCode.supportedCodes(), pageable);
    }

    @Test
    void shouldHideALegacyRoleById() {
        Role legacyRole = new Role();
        legacyRole.setId((short) 9);
        legacyRole.setCode("STAFF");
        when(repository.findById((short) 9)).thenReturn(Optional.of(legacyRole));

        assertThrows(ResourceNotFoundException.class, () -> service.findById((short) 9));
    }
}
