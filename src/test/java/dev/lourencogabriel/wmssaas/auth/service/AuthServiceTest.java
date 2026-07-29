package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USER_ID = "01JTESTUSER0000000000000000";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        this.authService = new AuthService(
                this.userRepository,
                this.passwordEncoder,
                this.authenticationManager,
                this.tokenService
        );
    }

    @Test
    void shouldAuthenticateAndGenerateTokensForAuthenticatedUser() {
        User user = this.user();
        Authentication authentication = mock(Authentication.class);
        AuthResponse expectedResponse = this.authResponse();
        when(authentication.getPrincipal()).thenReturn(user);
        when(this.authenticationManager.authenticate(any())).thenReturn(authentication);
        when(this.tokenService.generateTokens(USER_ID, "agent", "203.0.113.10"))
                .thenReturn(expectedResponse);

        AuthResponse response = this.authService.authenticate(
                "test@example.com",
                "plain-password",
                "agent",
                "203.0.113.10"
        );

        ArgumentCaptor<UsernamePasswordAuthenticationToken> authenticationCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(this.authenticationManager).authenticate(authenticationCaptor.capture());

        assertAll(
                () -> assertSame(expectedResponse, response),
                () -> assertEquals("test@example.com", authenticationCaptor.getValue().getPrincipal()),
                () -> assertEquals("plain-password", authenticationCaptor.getValue().getCredentials())
        );
    }

    @Test
    void shouldRegisterUserWithEncodedPasswordAndGenerateTokens() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "plain-password");
        AuthResponse expectedResponse = this.authResponse();
        when(this.userRepository.existsByEmail(request.email())).thenReturn(false);
        when(this.passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(this.userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setUlid(USER_ID);
            return savedUser;
        });
        when(this.tokenService.generateTokens(USER_ID, "agent", "203.0.113.10"))
                .thenReturn(expectedResponse);

        AuthResponse response = this.authService.register(
                request,
                "agent",
                "203.0.113.10"
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(this.userRepository).save(userCaptor.capture());

        assertAll(
                () -> assertSame(expectedResponse, response),
                () -> assertEquals(request.name(), userCaptor.getValue().getName()),
                () -> assertEquals(request.email(), userCaptor.getValue().getEmail()),
                () -> assertEquals("encoded-password", userCaptor.getValue().getPassword())
        );
    }

    @Test
    void shouldRejectRegistrationWhenEmailIsAlreadyInUse() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "plain-password");
        when(this.userRepository.existsByEmail(request.email())).thenReturn(true);

        EmailAlreadyInUseException exception = assertThrows(
                EmailAlreadyInUseException.class,
                () -> this.authService.register(request, null, null)
        );

        assertTrue(exception.getMessage().contains(request.email()));
        verify(this.userRepository, never()).save(any());
        verifyNoInteractions(this.passwordEncoder, this.tokenService);
    }

    @Test
    void shouldRejectBlankRefreshTokenBeforeDelegating() {
        JwtException exception = assertThrows(
                JwtException.class,
                () -> this.authService.refresh(" ", null, null)
        );

        assertEquals("Refresh token is required", exception.getMessage());
        verifyNoInteractions(this.tokenService);
    }

    @Test
    void shouldDelegateValidRefreshTokenRotation() {
        AuthResponse expectedResponse = this.authResponse();
        when(this.tokenService.refreshTokens("refresh-token", "agent", "203.0.113.10"))
                .thenReturn(expectedResponse);

        AuthResponse response = this.authService.refresh(
                "refresh-token",
                "agent",
                "203.0.113.10"
        );

        assertSame(expectedResponse, response);
    }

    private User user() {
        User user = new User("Test User", "test@example.com", "encoded-password");
        user.setUlid(USER_ID);
        return user;
    }

    private AuthResponse authResponse() {
        Instant now = Instant.now();
        return new AuthResponse(
                "https://api.wmssaas.com",
                now,
                now.plusSeconds(900),
                USER_ID,
                "access-token",
                "refresh-token",
                now.plus(30, ChronoUnit.DAYS)
        );
    }
}
