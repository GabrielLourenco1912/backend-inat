package dev.lourencogabriel.inatead.auth.service;

import dev.lourencogabriel.inatead.auth.dto.AuthResponse;
import dev.lourencogabriel.inatead.auth.dto.AuthChallengeResponse;
import dev.lourencogabriel.inatead.auth.dto.RegisterRequest;
import dev.lourencogabriel.inatead.auth.exception.PasswordResetUnavailableException;
import dev.lourencogabriel.inatead.auth.model.AuthChallengePurpose;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.inatead.user.exception.PersonAlreadyLinkedToUserException;
import dev.lourencogabriel.inatead.user.exception.PersonNotFoundForRegistrationException;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.service.UserRoleService;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USER_ID = "01JTESTUSER0000000000000000";
    private static final Instant REGULATION_ACCEPTED_AT =
            Instant.parse("2026-09-07T14:32:10.123Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthChallengeService challengeService;

    @Mock
    private AccountSecurityService accountSecurityService;

    @Mock
    private UserRoleService userRoleService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        this.authService = new AuthService(
                this.userRepository,
                this.personRepository,
                this.passwordEncoder,
                this.authenticationManager,
                this.tokenService,
                this.challengeService,
                this.accountSecurityService,
                this.userRoleService
        );
    }

    @Test
    void shouldAuthenticateAndIssueMfaChallengeForVerifiedUser() {
        User user = this.user();
        Authentication authentication = mock(Authentication.class);
        AuthChallengeResponse expectedResponse = this.challenge(
                AuthChallengePurpose.MFA_LOGIN
        );
        when(authentication.getPrincipal()).thenReturn(user);
        when(this.authenticationManager.authenticate(any())).thenReturn(authentication);
        when(this.challengeService.issue(
                user,
                AuthChallengePurpose.MFA_LOGIN,
                "agent",
                "203.0.113.10"
        ))
                .thenReturn(expectedResponse);

        AuthChallengeResponse response = this.authService.authenticate(
                "Test@Example.com",
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
        verify(this.accountSecurityService).unlockExpiredByEmail("test@example.com");
        verifyNoInteractions(this.tokenService);
    }

    @Test
    void shouldCountARejectedPasswordAttempt() {
        BadCredentialsException expected = new BadCredentialsException("Bad credentials");
        when(this.authenticationManager.authenticate(any())).thenThrow(expected);

        BadCredentialsException thrown = assertThrows(
                BadCredentialsException.class,
                () -> this.authService.authenticate(
                        "Test@Example.com",
                        "wrong-password",
                        "agent",
                        "203.0.113.10"
                )
        );

        assertSame(expected, thrown);
        verify(this.accountSecurityService).unlockExpiredByEmail("test@example.com");
        verify(this.accountSecurityService).recordFailedAuthentication("test@example.com");
        verifyNoInteractions(this.challengeService, this.tokenService);
    }

    @Test
    void shouldIssueEmailVerificationChallengeForUnverifiedUser() {
        User user = this.user();
        user.setEmailVerifiedAt(null);
        user.setStatus(UserAccountStatus.INVITED);
        Authentication authentication = mock(Authentication.class);
        AuthChallengeResponse expectedResponse = this.challenge(
                AuthChallengePurpose.EMAIL_VERIFICATION
        );
        when(authentication.getPrincipal()).thenReturn(user);
        when(this.authenticationManager.authenticate(any())).thenReturn(authentication);
        when(this.challengeService.issue(
                user,
                AuthChallengePurpose.EMAIL_VERIFICATION,
                null,
                null
        )).thenReturn(expectedResponse);

        AuthChallengeResponse response = this.authService.authenticate(
                "test@example.com",
                "plain-password"
        );

        assertSame(expectedResponse, response);
        verifyNoInteractions(this.tokenService);
    }

    @Test
    void shouldRegisterInvitedUserAndIssueEmailVerificationChallenge() {
        RegisterRequest request = this.registerRequest("Test@Example.com");
        Person person = this.person();
        AuthChallengeResponse expectedResponse = this.challenge(
                AuthChallengePurpose.EMAIL_VERIFICATION
        );
        when(this.userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(this.personRepository.findByContactEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(person));
        when(this.userRepository.existsByPerson_Id(person.getId())).thenReturn(false);
        when(this.passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(this.userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setUlid(USER_ID);
            return savedUser;
        });
        when(this.challengeService.issue(
                any(User.class),
                eq(AuthChallengePurpose.EMAIL_VERIFICATION),
                eq("agent"),
                eq("203.0.113.10")
        ))
                .thenReturn(expectedResponse);

        AuthChallengeResponse response = this.authService.register(
                request,
                "agent",
                "203.0.113.10"
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(this.userRepository).save(userCaptor.capture());
        verify(this.userRoleService).grantInitialRoles(userCaptor.getValue());

        assertAll(
                () -> assertSame(expectedResponse, response),
                () -> assertEquals(request.name(), userCaptor.getValue().getName()),
                () -> assertEquals("test@example.com", userCaptor.getValue().getEmail()),
                () -> assertEquals("encoded-password", userCaptor.getValue().getPassword()),
                () -> assertSame(person, userCaptor.getValue().getPerson()),
                () -> assertEquals(UserAccountStatus.INVITED, userCaptor.getValue().getStatus()),
                () -> assertTrue(userCaptor.getValue().isRegulationAccepted()),
                () -> assertEquals(
                        REGULATION_ACCEPTED_AT,
                        userCaptor.getValue().getRegulationAcceptedAt()
                ),
                () -> assertNull(userCaptor.getValue().getEmailVerifiedAt())
        );
        verifyNoInteractions(this.tokenService);
    }

    @Test
    void shouldRejectRegistrationWhenEmailIsAlreadyInUse() {
        RegisterRequest request = this.registerRequest("test@example.com");
        when(this.userRepository.existsByEmail(request.email())).thenReturn(true);

        EmailAlreadyInUseException exception = assertThrows(
                EmailAlreadyInUseException.class,
                () -> this.authService.register(request, null, null)
        );

        assertTrue(exception.getMessage().contains(request.email()));
        verify(this.userRepository, never()).save(any());
        verifyNoInteractions(this.passwordEncoder, this.tokenService, this.challengeService);
    }

    @Test
    void shouldRejectRegistrationWhenNoPersonHasTheSuppliedEmail() {
        RegisterRequest request = this.registerRequest("test@example.com");
        when(this.userRepository.existsByEmail(request.email())).thenReturn(false);
        when(this.personRepository.findByContactEmailIgnoreCase(request.email()))
                .thenReturn(Optional.empty());

        assertThrows(
                PersonNotFoundForRegistrationException.class,
                () -> this.authService.register(request, null, null)
        );

        verify(this.userRepository, never()).save(any());
        verify(this.userRepository, never()).existsByPerson_Id(any());
        verifyNoInteractions(this.passwordEncoder, this.tokenService, this.challengeService);
    }

    @Test
    void shouldRejectRegistrationWhenPersonIsAlreadyLinkedToAnotherUser() {
        RegisterRequest request = this.registerRequest("test@example.com");
        Person person = this.person();
        when(this.userRepository.existsByEmail(request.email())).thenReturn(false);
        when(this.personRepository.findByContactEmailIgnoreCase(request.email()))
                .thenReturn(Optional.of(person));
        when(this.userRepository.existsByPerson_Id(person.getId())).thenReturn(true);

        assertThrows(
                PersonAlreadyLinkedToUserException.class,
                () -> this.authService.register(request, null, null)
        );

        verify(this.userRepository, never()).save(any());
        verifyNoInteractions(this.passwordEncoder, this.tokenService, this.challengeService);
    }

    @ParameterizedTest
    @EnumSource(value = RecordStatus.class, names = {"INACTIVE", "SUSPENDED"})
    void shouldRejectRegistrationWhenPersonIsNotActive(RecordStatus personStatus) {
        RegisterRequest request = this.registerRequest("test@example.com");
        Person person = this.person();
        person.setStatus(personStatus);
        when(this.userRepository.existsByEmail(request.email())).thenReturn(false);
        when(this.personRepository.findByContactEmailIgnoreCase(request.email()))
                .thenReturn(Optional.of(person));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.authService.register(request, null, null)
        );

        assertEquals("User registration requires an active person", exception.getMessage());
        verify(this.userRepository, never()).existsByPerson_Id(any());
        verify(this.userRepository, never()).save(any());
        verifyNoInteractions(this.passwordEncoder, this.tokenService, this.challengeService);
    }

    @Test
    void shouldIssuePasswordResetChallengeForActiveUser() {
        User user = this.user();
        AuthChallengeResponse expectedResponse = this.challenge(
                AuthChallengePurpose.PASSWORD_RESET
        );
        when(this.userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));
        when(this.challengeService.issue(
                user,
                AuthChallengePurpose.PASSWORD_RESET,
                "agent",
                "203.0.113.10"
        )).thenReturn(expectedResponse);

        AuthChallengeResponse response = this.authService.requestPasswordReset(
                "Test@Example.com",
                "agent",
                "203.0.113.10"
        );

        assertSame(expectedResponse, response);
        verify(this.accountSecurityService).unlockExpiredByEmail("test@example.com");
        verifyNoInteractions(this.tokenService);
    }

    @Test
    void shouldRejectPasswordResetRequestForDisabledUser() {
        User user = this.user();
        user.setStatus(UserAccountStatus.DISABLED);
        when(this.userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(
                PasswordResetUnavailableException.class,
                () -> this.authService.requestPasswordReset(
                        "test@example.com",
                        null,
                        null
                )
        );

        verify(this.accountSecurityService).unlockExpiredByEmail("test@example.com");
        verifyNoInteractions(this.challengeService, this.tokenService);
    }

    @Test
    void shouldResetPasswordWithoutIssuingAuthentication() {
        User user = this.user();
        when(this.challengeService.verifyPasswordReset("challenge-id", "123456"))
                .thenReturn(user);
        when(this.passwordEncoder.encode("new-password")).thenReturn("new-password-hash");

        this.authService.resetPassword("challenge-id", "123456", "new-password");

        assertEquals("new-password-hash", user.getPassword());
        verify(this.accountSecurityService).applyAdministrativeUpdate(
                user,
                UserAccountStatus.ACTIVE,
                true
        );
        verifyNoInteractions(this.tokenService);
    }

    @Test
    void shouldGenerateTokensOnlyAfterChallengeVerification() {
        User user = this.user();
        AuthResponse expectedResponse = this.authResponse();
        when(this.challengeService.verify("challenge-id", "123456")).thenReturn(user);
        when(this.tokenService.generateTokens(USER_ID, "agent", "203.0.113.10"))
                .thenReturn(expectedResponse);

        AuthResponse response = this.authService.verifyChallenge(
                "challenge-id",
                "123456",
                "agent",
                "203.0.113.10"
        );

        assertSame(expectedResponse, response);
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

    @Test
    void shouldDelegateRefreshTokenRevocationOnLogout() {
        this.authService.logout("refresh-token");

        verify(this.tokenService).revokeRefreshToken("refresh-token");
    }

    private User user() {
        User user = new User("Test User", "test@example.com", "encoded-password");
        user.setUlid(USER_ID);
        user.setEmailVerifiedAt(Instant.parse("2026-09-04T12:00:00Z"));
        return user;
    }

    private Person person() {
        Person person = new Person();
        person.setId("01JTESTPERSON000000000000000");
        person.setContactEmail("test@example.com");
        return person;
    }

    private RegisterRequest registerRequest(String email) {
        return new RegisterRequest(
                "Test User",
                email,
                true,
                REGULATION_ACCEPTED_AT,
                "plain-password"
        );
    }

    private AuthResponse authResponse() {
        Instant now = Instant.now();
        return new AuthResponse(
                "inat-ead-backend-test",
                now,
                now.plusSeconds(900),
                USER_ID,
                "access-token",
                "refresh-token",
                now.plus(30, ChronoUnit.DAYS)
        );
    }

    private AuthChallengeResponse challenge(AuthChallengePurpose purpose) {
        return new AuthChallengeResponse(
                "01JTESTCHALLENGE000000000000",
                purpose,
                "t***@example.com",
                Instant.parse("2026-09-04T12:10:00Z"),
                5
        );
    }
}
