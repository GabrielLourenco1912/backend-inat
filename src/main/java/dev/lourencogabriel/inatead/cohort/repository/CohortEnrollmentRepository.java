package dev.lourencogabriel.inatead.cohort.repository;

import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CohortEnrollmentRepository extends JpaRepository<CohortEnrollment, String> {

    boolean existsByContract_Id(String contractId);

    List<CohortEnrollment> findAllByContract_Id(String contractId);

    boolean existsByCohort_Id(String cohortId);

    List<CohortEnrollment> findAllByCohort_Id(String cohortId);

    long countByCohort_IdAndStatusIn(
            String cohortId,
            Collection<EnrollmentStatus> statuses
    );

    long countByCohort_IdAndStatusInAndIdNot(
            String cohortId,
            Collection<EnrollmentStatus> statuses,
            String id
    );

    List<CohortEnrollment> findAllByContract_IdAndStatusIn(
            String contractId,
            Collection<EnrollmentStatus> statuses
    );

    List<CohortEnrollment> findAllByCohort_IdAndStatusIn(
            String cohortId,
            Collection<EnrollmentStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select enrollment from CohortEnrollment enrollment where enrollment.id = :id")
    Optional<CohortEnrollment> findByIdForUpdate(@Param("id") String id);

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
              and e.cohort.status = dev.lourencogabriel.inatead.shared.model.CohortStatus.ACTIVE
              and e.contract.status = dev.lourencogabriel.inatead.shared.model.ContractStatus.ACTIVE
              and e.contract.learner.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and e.contract.learner.person.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and e.startDate <= :lessonDate
              and (e.endDate is null or e.endDate >= :lessonDate)
            """)
    List<CohortEnrollment> findActiveRoster(
            @Param("cohortId") String cohortId,
            @Param("lessonDate") LocalDate lessonDate
    );

    @Query("""
            select enrollment.id
            from CohortEnrollment enrollment
            where enrollment.status = :status
              and enrollment.endDate is not null
              and enrollment.endDate < :today
            order by enrollment.endDate, enrollment.id
            """)
    List<String> findCompletionCandidateIds(
            @Param("status") EnrollmentStatus status,
            @Param("today") LocalDate today
    );
}
