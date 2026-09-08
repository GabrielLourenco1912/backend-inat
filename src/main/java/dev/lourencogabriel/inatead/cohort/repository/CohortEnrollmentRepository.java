package dev.lourencogabriel.inatead.cohort.repository;

import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CohortEnrollmentRepository extends JpaRepository<CohortEnrollment, String> {

    @Query("""
            select (count(e) > 0)
            from CohortEnrollment e
            where e.contract.id = :contractId
              and e.status in :statuses
              and (:currentId is null or e.id <> :currentId)
              and e.startDate <= :rangeEnd
              and (e.endDate is null or e.endDate >= :rangeStart)
            """)
    boolean existsOverlappingEnrollment(
            @Param("contractId") String contractId,
            @Param("statuses") Collection<EnrollmentStatus> statuses,
            @Param("currentId") String currentId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );

    @EntityGraph(attributePaths = {"contract", "contract.learner"})
    @Query("""
            select e
            from CohortEnrollment e
            where e.cohort.id = :cohortId
              and e.status = dev.lourencogabriel.inatead.shared.model.EnrollmentStatus.ACTIVE
              and e.startDate <= :lessonDate
              and (e.endDate is null or e.endDate >= :lessonDate)
            """)
    List<CohortEnrollment> findActiveRoster(
            @Param("cohortId") String cohortId,
            @Param("lessonDate") LocalDate lessonDate
    );
}
