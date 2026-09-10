package dev.lourencogabriel.inatead.user.repository;

import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.user.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    UserDetails findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndUlidNot(String email, String id);

    boolean existsByPerson_Id(String personId);

    boolean existsByPerson_IdAndUlidNot(String personId, String id);

    Optional<User> findByPerson_IdAndStatus(String personId, UserAccountStatus status);

    @EntityGraph(attributePaths = {"person", "userRoles", "userRoles.role"})
    Optional<User> findWithRolesByUlid(String ulid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where lower(user.email) = lower(:email)")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @EntityGraph(attributePaths = {"person", "userRoles", "userRoles.role"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.ulid = :userId")
    Optional<User> findWithRolesByUlidForUpdate(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findAllByStatusAndLockedUntilLessThanEqual(
            UserAccountStatus status,
            Instant lockedUntil
    );

    List<User> findAllByStatusOrderByUlid(UserAccountStatus status);

    @Query("""
            select distinct u
            from CohortEnrollment e
            join e.contract c
            join c.learner l
            join User u on u.person = l.person
            where e.cohort.id = :cohortId
              and e.status = dev.lourencogabriel.inatead.shared.model.EnrollmentStatus.ACTIVE
              and e.cohort.status = dev.lourencogabriel.inatead.shared.model.CohortStatus.ACTIVE
              and c.status = dev.lourencogabriel.inatead.shared.model.ContractStatus.ACTIVE
              and e.startDate <= :referenceDate
              and (e.endDate is null or e.endDate >= :referenceDate)
              and l.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and l.person.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and u.status = dev.lourencogabriel.inatead.shared.model.UserAccountStatus.ACTIVE
            order by u.ulid
            """)
    List<User> findActiveCohortRecipients(
            @Param("cohortId") String cohortId,
            @Param("referenceDate") LocalDate referenceDate
    );

    @Query("""
            select distinct u
            from CohortEnrollment e
            join e.contract c
            join c.learner learner
            join User u on u.person = learner.person
            where e.cohort.id = :cohortId
              and e.status = dev.lourencogabriel.inatead.shared.model.EnrollmentStatus.ACTIVE
              and e.cohort.status = dev.lourencogabriel.inatead.shared.model.CohortStatus.ACTIVE
              and c.status = dev.lourencogabriel.inatead.shared.model.ContractStatus.ACTIVE
              and e.startDate <= :referenceDate
              and (e.endDate is null or e.endDate >= :referenceDate)
              and learner.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and learner.person.status = dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE
              and u.status = dev.lourencogabriel.inatead.shared.model.UserAccountStatus.ACTIVE
              and not exists (
                  select submission.id
                  from ActivitySubmission submission
                  where submission.activity.id = :activityId
                    and submission.learner = learner
                    and submission.status in :deliveredStatuses
              )
            order by u.ulid
            """)
    List<User> findActiveCohortRecipientsWithoutDeliveredSubmission(
            @Param("cohortId") String cohortId,
            @Param("activityId") String activityId,
            @Param("referenceDate") LocalDate referenceDate,
            @Param("deliveredStatuses") Collection<SubmissionStatus> deliveredStatuses
    );

    @Query("""
            select (count(u) > 0)
            from User u
            join u.userRoles ur
            where u.person.id = :personId
              and u.status = :status
              and ur.role.code = :roleCode
            """)
    boolean existsActivePersonWithRole(
            @Param("personId") String personId,
            @Param("status") UserAccountStatus status,
            @Param("roleCode") String roleCode
    );
}
