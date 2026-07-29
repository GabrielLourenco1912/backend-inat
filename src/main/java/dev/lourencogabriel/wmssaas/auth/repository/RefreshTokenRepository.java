package dev.lourencogabriel.wmssaas.auth.repository;

import dev.lourencogabriel.wmssaas.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    RefreshTokenRepository findByTokenHash(String tokenHash);
}
