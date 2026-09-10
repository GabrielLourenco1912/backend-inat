package dev.lourencogabriel.inatead.auth.security;

import dev.lourencogabriel.inatead.auth.service.TokenService;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ActiveUserJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ACCOUNT = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The token no longer belongs to an active session",
            null
    );

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        Object tokenVersionClaim = token.getClaim(TokenService.TOKEN_VERSION_CLAIM);
        if (!StringUtils.hasText(subject) || !(tokenVersionClaim instanceof Number tokenVersion)) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
        }

        boolean valid = this.userRepository.findById(subject)
                .filter(user -> user.getStatus() == UserAccountStatus.ACTIVE)
                .filter(user -> user.getEmailVerifiedAt() != null)
                .map(user -> tokenVersion(user) == tokenVersion.longValue())
                .orElse(false);

        return valid
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
    }

    private static long tokenVersion(User user) {
        return user.getTokenVersion() == null ? 0L : user.getTokenVersion();
    }
}
