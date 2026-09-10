package dev.lourencogabriel.inatead.auth.security;

import dev.lourencogabriel.inatead.auth.service.TokenService;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveUserJwtValidatorTest {

    private static final String USER_ID = "01JTESTUSER0000000000000000";

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldAcceptMatchingTokenForActiveVerifiedUser() {
        User user = user(UserAccountStatus.ACTIVE, 7L);
        when(this.userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result = validator().validate(jwt(7L));

        assertFalse(result.hasErrors());
    }

    @Test
    void shouldRejectAccessTokenImmediatelyAfterAccountIsDisabled() {
        User user = user(UserAccountStatus.DISABLED, 7L);
        when(this.userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result = validator().validate(jwt(7L));

        assertTrue(result.hasErrors());
    }

    @Test
    void shouldRejectAccessTokenIssuedBeforeSessionInvalidation() {
        User user = user(UserAccountStatus.ACTIVE, 8L);
        when(this.userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result = validator().validate(jwt(7L));

        assertTrue(result.hasErrors());
    }

    @Test
    void shouldRejectLegacyTokenWithoutVersionClaim() {
        Jwt token = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(USER_ID)
                .issuedAt(Instant.parse("2026-09-08T13:00:00Z"))
                .expiresAt(Instant.parse("2026-09-08T15:00:00Z"))
                .build();

        OAuth2TokenValidatorResult result = validator().validate(token);

        assertTrue(result.hasErrors());
    }

    private ActiveUserJwtValidator validator() {
        return new ActiveUserJwtValidator(this.userRepository);
    }

    private User user(UserAccountStatus status, long tokenVersion) {
        User user = new User("Test User", "test@example.com", "hash");
        user.setUlid(USER_ID);
        user.setStatus(status);
        user.setEmailVerifiedAt(Instant.parse("2026-09-08T12:00:00Z"));
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private Jwt jwt(long tokenVersion) {
        return Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(USER_ID)
                .issuedAt(Instant.parse("2026-09-08T13:00:00Z"))
                .expiresAt(Instant.parse("2026-09-08T15:00:00Z"))
                .claim(TokenService.TOKEN_VERSION_CLAIM, tokenVersion)
                .build();
    }
}
