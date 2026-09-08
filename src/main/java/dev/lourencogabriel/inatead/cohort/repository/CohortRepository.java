package dev.lourencogabriel.inatead.cohort.repository;

import dev.lourencogabriel.inatead.cohort.model.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortRepository extends JpaRepository<Cohort, String> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);
}
