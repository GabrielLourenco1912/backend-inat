package dev.lourencogabriel.inatead.cohort.repository;

import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CohortRepository extends JpaRepository<Cohort, String> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cohort from Cohort cohort where cohort.id = :id")
    Optional<Cohort> findByIdForUpdate(@Param("id") String id);

    @Query("""
            select cohort.id
            from Cohort cohort
            where cohort.status in :statuses
              and cohort.endDate is not null
              and cohort.endDate < :today
            order by cohort.endDate, cohort.id
            """)
    List<String> findCompletionCandidateIds(
            @Param("statuses") Collection<CohortStatus> statuses,
            @Param("today") LocalDate today
    );
}
