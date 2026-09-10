package dev.lourencogabriel.inatead.auth.service;

import dev.lourencogabriel.inatead.auth.dto.AuthChallengeResponse;
import dev.lourencogabriel.inatead.auth.exception.InvalidAuthChallengeException;
import dev.lourencogabriel.inatead.auth.model.AuthChallenge;
import dev.lourencogabriel.inatead.auth.model.AuthChallengePurpose;
import dev.lourencogabriel.inatead.auth.repository.AuthChallengeRepository;
import dev.lourencogabriel.inatead.mail.EmailSender;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthChallengeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final Pattern CODE_LINE = Pattern.compile("(?m)^(\\d{6})$");

    @Mock
    private AuthChallengeRepository challengeRepository;
    @Mock
    private EmailSender emailSender;
    @Mock
    private AccountSecurityService accountSecurityService;

    private AuthChallengeService service;
    private AtomicReference<AuthChallenge> savedChallenge;
    private AtomicReference<String> emailBody;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(
                "test-only-secret-key-with-at-least-32-characters"
                        .getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        this.service = new AuthChallengeService(
                this.challengeRepository,
                this.emailSender,
                secretKey,
                Clock.fixed(NOW, ZoneOffset.UTC),
                this.accountSecurityService,
                Duration.ofMinutes(10),
                2
        );
        this.savedChallenge = new AtomicReference<>();
        this.emailBody = new AtomicReference<>();

        when(this.challengeRepository.save(any(AuthChallenge.class))).thenAnswer(invocation -> {
            AuthChallenge challenge = invocation.getArgument(0);
            if (challenge.getId() == null) {
                challenge.setId("01JTESTCHALLENGE000000000000");
            }
            this.savedChallenge.set(challenge);
            return challenge;
        });
        doAnswer(invocation -> {
            this.emailBody.set(invocation.getArgument(2));
            return null;
        }).when(this.emailSender).send(any(), any(), any());
    }

    @Test
    void shouldIssueSingleUseMfaCodeWithoutPersistingItsPlainValue() {
        User user = verifiedUser();

        AuthChallengeResponse response = this.service.issue(
                user,
                AuthChallengePurpose.MFA_LOGIN,
                "agent",
                "203.0.113.10"
        );

        String code = issuedCode();
        AuthChallenge challenge = this.savedChallenge.get();
        assertEquals("01JTESTCHALLENGE000000000000", response.challengeId());
        assertEquals(AuthChallengePurpose.MFA_LOGIN, response.purpose());
        assertEquals("t***@example.com", response.maskedEmail());
        assertEquals(NOW.plusSeconds(600), response.expiresAt());
        assertEquals(2, response.maxAttempts());
        assertNotEquals(code, challenge.getCodeDigest());
        assertEquals(64, challenge.getCodeDigest().length());
        assertEquals(32, challenge.getCodeNonce().length());
        verify(this.challengeRepository).invalidateActiveForUser(user.getUlid(), NOW);
        verify(this.emailSender).send(
                eq("test@example.com"),
                eq("Seu código de acesso ao INAT EAD"),
                any()
        );
    }

    @Test
    void shouldVerifyEmailAndActivateInvitedUser() {
        User user = verifiedUser();
        user.setEmailVerifiedAt(null);
        user.setStatus(UserAccountStatus.INVITED);
        this.service.issue(user, AuthChallengePurpose.EMAIL_VERIFICATION, null, null);
        AuthChallenge challenge = this.savedChallenge.get();
        when(this.challengeRepository.findWithUserById(challenge.getId()))
                .thenReturn(Optional.of(challenge));

        User verified = this.service.verify(challenge.getId(), issuedCode());

        assertEquals(user, verified);
        assertEquals(UserAccountStatus.ACTIVE, user.getStatus());
        assertEquals(NOW, user.getEmailVerifiedAt());
        assertEquals(NOW, challenge.getConsumedAt());
    }

    @Test
    void shouldConsumeChallengeAfterMaximumFailedAttempts() {
        this.service.issue(verifiedUser(), AuthChallengePurpose.MFA_LOGIN, null, null);
        AuthChallenge challenge = this.savedChallenge.get();
        when(this.challengeRepository.findWithUserById(challenge.getId()))
                .thenReturn(Optional.of(challenge));
        String issuedCode = issuedCode();
        String firstWrongCode = issuedCode.equals("000000") ? "000001" : "000000";
        String secondWrongCode = issuedCode.equals("999999") ? "999998" : "999999";

        assertThrows(
                InvalidAuthChallengeException.class,
                () -> this.service.verify(challenge.getId(), firstWrongCode)
        );
        assertEquals(1, challenge.getFailedAttempts());
        assertNull(challenge.getConsumedAt());

        assertThrows(
                InvalidAuthChallengeException.class,
                () -> this.service.verify(challenge.getId(), secondWrongCode)
        );
        assertEquals(2, challenge.getFailedAttempts());
        assertNotNull(challenge.getConsumedAt());
        verify(this.accountSecurityService, times(2))
                .recordFailedAuthentication(challenge.getUser());
    }

    @Test
    void shouldRejectAndConsumeExpiredChallenge() {
        this.service.issue(verifiedUser(), AuthChallengePurpose.MFA_LOGIN, null, null);
        AuthChallenge challenge = this.savedChallenge.get();
        challenge.setExpiresAt(NOW);
        when(this.challengeRepository.findWithUserById(challenge.getId()))
                .thenReturn(Optional.of(challenge));

        assertThrows(
                InvalidAuthChallengeException.class,
                () -> this.service.verify(challenge.getId(), issuedCode())
        );

        assertNotNull(challenge.getConsumedAt());
    }

    @Test
    void shouldRejectChallengeAfterUserEmailChanges() {
        User user = verifiedUser();
        user.setEmailVerifiedAt(null);
        user.setStatus(UserAccountStatus.INVITED);
        this.service.issue(user, AuthChallengePurpose.EMAIL_VERIFICATION, null, null);
        AuthChallenge challenge = this.savedChallenge.get();
        String code = issuedCode();
        user.setEmail("new-address@example.com");
        when(this.challengeRepository.findWithUserById(challenge.getId()))
                .thenReturn(Optional.of(challenge));

        assertThrows(
                InvalidAuthChallengeException.class,
                () -> this.service.verify(challenge.getId(), code)
        );

        assertNull(user.getEmailVerifiedAt());
        assertNotNull(challenge.getConsumedAt());
    }

    @Test
    void shouldVerifyPasswordResetWithoutChangingAccountState() {
        User user = verifiedUser();
        Instant verifiedAt = user.getEmailVerifiedAt();
        this.service.issue(user, AuthChallengePurpose.PASSWORD_RESET, null, null);
        AuthChallenge challenge = this.savedChallenge.get();
        when(this.challengeRepository.findWithUserById(challenge.getId()))
                .thenReturn(Optional.of(challenge));

        User verified = this.service.verifyPasswordReset(challenge.getId(), issuedCode());

        assertEquals(user, verified);
        assertEquals(UserAccountStatus.ACTIVE, user.getStatus());
        assertEquals(verifiedAt, user.getEmailVerifiedAt());
        assertEquals(NOW, challenge.getConsumedAt());
        verify(this.accountSecurityService).recordSuccessfulAuthentication(user);
        verify(this.emailSender).send(
                eq("test@example.com"),
                eq("Redefina sua senha no INAT EAD"),
                any()
        );
    }

    @Test
    void shouldNotAcceptPasswordResetChallengeAsAuthentication() {
        this.service.issue(
                verifiedUser(),
                AuthChallengePurpose.PASSWORD_RESET,
                null,
                null
        );
        AuthChallenge challenge = this.savedChallenge.get();
        when(this.challengeRepository.findWithUserById(challenge.getId()))
                .thenReturn(Optional.of(challenge));

        assertThrows(
                InvalidAuthChallengeException.class,
                () -> this.service.verify(challenge.getId(), issuedCode())
        );

        assertEquals(NOW, challenge.getConsumedAt());
    }

    private User verifiedUser() {
        User user = new User("Test User", "test@example.com", "hash");
        user.setUlid("01JTESTUSER0000000000000000");
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(NOW.minusSeconds(60));
        return user;
    }

    private String issuedCode() {
        Matcher matcher = CODE_LINE.matcher(this.emailBody.get());
        assertFalse(this.emailBody.get().isBlank());
        if (!matcher.find()) {
            throw new AssertionError("The email did not contain a six-digit code line");
        }
        return matcher.group(1);
    }
}
