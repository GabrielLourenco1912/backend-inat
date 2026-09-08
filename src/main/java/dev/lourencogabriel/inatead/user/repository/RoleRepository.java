package dev.lourencogabriel.inatead.user.repository;

import dev.lourencogabriel.inatead.user.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface RoleRepository extends JpaRepository<Role, Short> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Short id);

    Page<Role> findAllByCodeIn(Collection<String> codes, Pageable pageable);
}
