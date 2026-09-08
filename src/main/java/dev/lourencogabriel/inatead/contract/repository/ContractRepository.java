package dev.lourencogabriel.inatead.contract.repository;

import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, String> {

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

    List<Contract> findAllByLearner_IdOrderByStartDateDesc(String learnerId);

    @Query("""
            select c
            from Contract c
            where c.employer.id = :organizationId
               or c.school.id = :organizationId
            order by c.startDate desc
            """)
    List<Contract> findAllByOrganization(@Param("organizationId") String organizationId);
}
