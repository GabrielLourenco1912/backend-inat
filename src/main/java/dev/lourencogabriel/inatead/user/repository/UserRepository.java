package dev.lourencogabriel.inatead.user.repository;

import dev.lourencogabriel.inatead.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserRepository extends JpaRepository<User, String> {
    UserDetails findByEmail(String email);

    boolean existsByEmail(String email);
}
