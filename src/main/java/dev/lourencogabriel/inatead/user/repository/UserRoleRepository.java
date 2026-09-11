package dev.lourencogabriel.inatead.user.repository;

import dev.lourencogabriel.inatead.user.model.UserRole;
import dev.lourencogabriel.inatead.user.model.UserRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("select distinct ur.role.code from UserRole ur where ur.user.person.id = :personId")
    List<String> findRoleCodesByPersonId(@Param("personId") String personId);

    @EntityGraph(attributePaths = {"role", "grantedByUser"})
    List<UserRole> findAllByUser_Ulid(String userId);

    boolean existsByUser_UlidAndRole_CodeIn(String userId, Collection<String> roleCodes);

    @Query("""
            select count(ur)
            from UserRole ur
            where ur.role.code = :roleCode
              and ur.user.status = dev.lourencogabriel.inatead.shared.model.UserAccountStatus.ACTIVE
            """)
    long countActiveUsersByRoleCode(@Param("roleCode") String roleCode);
}
