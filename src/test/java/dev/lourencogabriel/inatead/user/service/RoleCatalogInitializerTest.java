package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleCatalogInitializerTest {

    @Mock
    private RoleRepository repository;

    @Test
    void shouldInitializeExactlyTheFourSupportedRoles() {
        when(repository.existsByCode(anyString())).thenReturn(false);
        RoleCatalogInitializer initializer = new RoleCatalogInitializer(repository);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(repository, times(4)).save(captor.capture());
        assertEquals(
                SystemRoleCode.supportedCodes(),
                captor.getAllValues().stream()
                        .map(Role::getCode)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }
}
