package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final TokenService tokenService;

    @Transactional
    public AuthResponse authenticate(String email, String password) {
        return this.authenticate(email, password, null, null);
    }

    @Transactional
    public AuthResponse authenticate(String email, String password, String userAgent, String ipAddress) {
        User currentUser = this.authenticateUser(email, password);
        return this.tokenService.generateTokens(currentUser.getUlid(), userAgent, ipAddress);
    }

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        return this.register(registerRequest, null, null);
    }

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest, String userAgent, String ipAddress) {
        this.ensureEmailIsAvailable(registerRequest.email());

        User newUser = this.buildUser(registerRequest);
        User savedUser = this.userRepository.save(newUser);

        return this.tokenService.generateTokens(savedUser.getUlid(), userAgent, ipAddress);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken, String userAgent, String ipAddress) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new JwtException("Refresh token is required");
        }

        return this.tokenService.refreshTokens(refreshToken, userAgent, ipAddress);
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

    private User buildUser(RegisterRequest registerRequest) {
        String encodedPassword = this.passwordEncoder.encode(registerRequest.password());
        return new User(registerRequest.name(), registerRequest.email(), encodedPassword);
    }

    private void ensureEmailIsAvailable(String email) {
        if (this.userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email);
        }
    }
}
