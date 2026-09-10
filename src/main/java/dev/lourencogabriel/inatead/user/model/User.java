package dev.lourencogabriel.inatead.user.model;

import dev.lourencogabriel.inatead.shared.persistence.GeneratedUlid;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(
    name = "users",
    uniqueConstraints = {
            @UniqueConstraint(name = "uk_users_login_email", columnNames = "login_email"),
            @UniqueConstraint(name = "uk_users_person_id", columnNames = "person_id")
    }
)
public class User implements UserDetails {

    @Id
    @GeneratedUlid
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String ulid;

    @NotNull
    @Column(name = "display_name", length = 150, nullable = false)
    private String name;

    @NotNull
    @Column(name = "login_email", length = 254, nullable = false)
    private String email;

    @NotNull
    @Column(name = "regulation_accepted")
    private boolean regulationAccepted = false;

    @Column(name = "regulation_accepted_at")
    private Instant regulationAcceptedAt;

    @NotNull
    @Column(name = "password_hash", length = 255, nullable = false)
    private String password;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UserAccountStatus status = UserAccountStatus.ACTIVE;

    @Column(name = "failed_authentication_attempts")
    private Integer failedAuthenticationAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_lock", length = 20)
    private UserAccountStatus statusBeforeLock;

    @Column(name = "token_version")
    private Long tokenVersion = 0L;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> userRoles = new HashSet<>();

    @Column(name = "created_at",  nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "updated_at", nullable = false)
    @org.hibernate.annotations.UpdateTimestamp
    private Instant updatedAt;

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @NullMarked
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.userRoles == null || this.userRoles.isEmpty()) {
            return Set.of();
        }

        return this.userRoles.stream()
                .map(UserRole::getRole)
                .filter(java.util.Objects::nonNull)
                .filter(role -> SystemRoleCode.isSupported(role.getCode()))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @NullMarked
    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.status != UserAccountStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return this.status == UserAccountStatus.ACTIVE
                || this.status == UserAccountStatus.INVITED;
    }

    @PrePersist
    private void initAttributes() {
        if (this.status == null) {
            this.status = UserAccountStatus.ACTIVE;
        }
        if (this.failedAuthenticationAttempts == null) {
            this.failedAuthenticationAttempts = 0;
        }
        if (this.tokenVersion == null) {
            this.tokenVersion = 0L;
        }
        this.updatedAt = Instant.now();
    }
}
