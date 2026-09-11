package dev.lourencogabriel.inatead.organization.repository;

import dev.lourencogabriel.inatead.organization.model.OrganizationMembership;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, String> {

    @Query("select distinct m.membershipRole from OrganizationMembership m where m.person.id = :personId")
    List<String> findRoleCodesByPersonId(@Param("personId") String personId);

    @EntityGraph(attributePaths = "organization")
    @Query("""
            select membership
            from OrganizationMembership membership
            where membership.person.id = :personId
              and membership.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and membership.startDate <= :date
              and (membership.endDate is null or membership.endDate >= :date)
            order by membership.startDate desc
            """)
    List<OrganizationMembership> findActiveByPersonId(
            @Param("personId") String personId,
            @Param("date") LocalDate date
    );

    boolean existsByPerson_IdAndOrganization_IdAndMembershipRoleAndStartDate(
            String personId,
            String organizationId,
            String membershipRole,
            LocalDate startDate
    );

    boolean existsByPerson_IdAndOrganization_IdAndMembershipRoleAndStartDateAndIdNot(
            String personId,
            String organizationId,
            String membershipRole,
            LocalDate startDate,
            String id
    );

    boolean existsByPerson_IdAndOrganization_IdAndStatus(
            String personId,
            String organizationId,
            RecordStatus status
    );

    @Query("""
            select (count(m) > 0)
            from OrganizationMembership m
            where m.person.id = :personId
              and m.organization.id = :organizationId
              and m.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and m.startDate <= :date
              and (m.endDate is null or m.endDate >= :date)
            """)
    boolean existsActiveMembership(
            @Param("personId") String personId,
            @Param("organizationId") String organizationId,
            @Param("date") LocalDate date
    );
}
