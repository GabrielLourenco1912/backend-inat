package dev.lourencogabriel.inatead.contract.repository;

import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, String> {

    @Query(value="""
            select count(*) from contracts c
            where c.learner_id=:learnerId
              and exists(select 1 from organization_memberships m
                where m.organization_id=c.employer_id and m.person_id=:personId
                  and m.status='ACTIVE' and m.start_date<=:today
                  and (m.end_date is null or m.end_date>=:today))
              and exists(select 1 from organization_type_assignments ot
                where ot.organization_id=c.employer_id and ot.organization_type_code='EMPLOYER')
            """,nativeQuery=true)
    long countAccessibleByLearnerAndManager(@Param("learnerId") String learnerId,@Param("personId") String personId,@Param("today") LocalDate today);

    @Override
    @EntityGraph(attributePaths = {"learner.person", "employer", "school"})
    Page<Contract> findAll(Pageable pageable);

    @Query("""
            select (count(c) > 0)
            from Contract c
            where c.learner.id = :learnerId
              and c.status in :statuses
              and (:currentId is null or c.id <> :currentId)
              and c.startDate <= :rangeEnd
              and (c.endDate is null or c.endDate >= :rangeStart)
            """)
    boolean existsOverlappingContract(
            @Param("learnerId") String learnerId,
            @Param("statuses") Collection<ContractStatus> statuses,
            @Param("currentId") String currentId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );

    boolean existsByEmployer_Id(String employerId);

    boolean existsBySchool_Id(String schoolId);

    boolean existsByLearner_Id(String learnerId);

    @EntityGraph(attributePaths = {"learner.person", "employer", "school"})
    List<Contract> findAllByLearner_IdOrderByStartDateDesc(String learnerId);

    boolean existsByLearner_IdAndStatusIn(
            String learnerId,
            Collection<ContractStatus> statuses
    );

    boolean existsByEmployer_IdAndStatusIn(
            String employerId,
            Collection<ContractStatus> statuses
    );

    boolean existsBySchool_IdAndStatusIn(
            String schoolId,
            Collection<ContractStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contract from Contract contract where contract.id = :id")
    Optional<Contract> findByIdForUpdate(@Param("id") String id);

    @Query("""
            select contract.id
            from Contract contract
            where contract.status in :statuses
              and contract.endDate is not null
              and contract.endDate < :today
            order by contract.endDate, contract.id
            """)
    List<String> findEndingCandidateIds(
            @Param("statuses") Collection<ContractStatus> statuses,
            @Param("today") LocalDate today
    );

    @Query("""
            select c
            from Contract c
            where c.employer.id = :organizationId
               or c.school.id = :organizationId
            order by c.startDate desc
            """)
    @EntityGraph(attributePaths = {"learner.person", "employer", "school"})
    List<Contract> findAllByOrganization(@Param("organizationId") String organizationId);
}
