package dev.lourencogabriel.inatead.auth.service;

import dev.lourencogabriel.inatead.auth.dto.AuthResponse;
import dev.lourencogabriel.inatead.auth.dto.AuthChallengeResponse;
import dev.lourencogabriel.inatead.auth.dto.RegisterRequest;
import dev.lourencogabriel.inatead.auth.exception.InvalidAuthChallengeException;
import dev.lourencogabriel.inatead.auth.model.AuthChallengePurpose;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.inatead.user.exception.PersonAlreadyLinkedToUserException;
import dev.lourencogabriel.inatead.user.exception.PersonNotFoundForRegistrationException;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final TokenService tokenService;
    private final AuthChallengeService challengeService;

    @Transactional
    public AuthChallengeResponse authenticate(String email, String password) {
        return this.authenticate(email, password, null, null);
    }

    @Transactional
    public AuthChallengeResponse authenticate(
            String email,
            String password,
            String userAgent,
            String ipAddress
    ) {
        User currentUser = this.authenticateUser(this.normalizeEmail(email), password);
        AuthChallengePurpose purpose = currentUser.getEmailVerifiedAt() == null
                ? AuthChallengePurpose.EMAIL_VERIFICATION
                : AuthChallengePurpose.MFA_LOGIN;
        return this.challengeService.issue(currentUser, purpose, userAgent, ipAddress);
    }

    @Transactional
    public AuthChallengeResponse register(RegisterRequest registerRequest) {
        return this.register(registerRequest, null, null);
    }

    @Transactional
    public AuthChallengeResponse register(
            RegisterRequest registerRequest,
            String userAgent,
            String ipAddress
    ) {
        String email = this.normalizeEmail(registerRequest.email());
        this.ensureEmailIsAvailable(email);
        Person person = this.resolvePersonForRegistration(email);
        this.ensurePersonIsAvailable(person);

        User newUser = this.buildUser(registerRequest, email, person);
        User savedUser = this.userRepository.save(newUser);

        return this.challengeService.issue(
                savedUser,
                AuthChallengePurpose.EMAIL_VERIFICATION,
                userAgent,
                ipAddress
        );
    }

    @Transactional(noRollbackFor = InvalidAuthChallengeException.class)
    public AuthResponse verifyChallenge(
            String challengeId,
            String code,
            String userAgent,
            String ipAddress
    ) {
        User user = this.challengeService.verify(challengeId, code);
        return this.tokenService.generateTokens(user.getUlid(), userAgent, ipAddress);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken, String userAgent, String ipAddress) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new JwtException("Refresh token is required");
        }

        return this.tokenService.refreshTokens(refreshToken, userAgent, ipAddress);
    }

    @Transactional
    public void logout(String refreshToken) {
        this.tokenService.revokeRefreshToken(refreshToken);
    }

    private User authenticateUser(String email, String password) {
        Authentication authentication = this.authManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        if (authentication.getPrincipal() instanceof User user) {
            return user;
        }

        throw new IllegalStateException("Authenticated principal is not a User");
    }

    private User buildUser(RegisterRequest registerRequest, String email, Person person) {
        String encodedPassword = this.passwordEncoder.encode(registerRequest.password());
        User user = new User(registerRequest.name().trim(), email, encodedPassword);
        user.setRegulationAccepted(registerRequest.regulationAccepted());
        user.setRegulationAcceptedAt(registerRequest.regulationAcceptedAt());
        user.setPerson(person);
        user.setStatus(UserAccountStatus.INVITED);
        return user;
    }

    private void ensureEmailIsAvailable(String email) {
        if (this.userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email);
        }
    }

    private Person resolvePersonForRegistration(String email) {
        return this.personRepository.findByContactEmailIgnoreCase(email)
                .orElseThrow(PersonNotFoundForRegistrationException::new);
    }

    private void ensurePersonIsAvailable(Person person) {
        if (this.userRepository.existsByPerson_Id(person.getId())) {
            throw new PersonAlreadyLinkedToUserException();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
