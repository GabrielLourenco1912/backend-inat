package dev.lourencogabriel.inatead.lesson.repository;

import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, String> {

    boolean existsByLessonParticipant_Id(String participantId);

    boolean existsByLessonParticipant_IdAndIdNot(String participantId, String id);

    List<AttendanceRecord> findAllByLessonParticipant_Lesson_IdOrderByRecordedAtAsc(
            String lessonId
    );

    @Query(
            value = """
                    select distinct attendance
                    from AttendanceRecord attendance
                    join attendance.lessonParticipant participant
                    join participant.lesson lesson
                    where (:startsAt is null or lesson.startsAt >= :startsAt)
                      and (:endsAtExclusive is null or lesson.startsAt < :endsAtExclusive)
                      and (:learnerId is null or participant.learner.id = :learnerId)
                      and (:instructorPersonId is null
                           or lesson.instructorPerson.id = :instructorPersonId)
                      and (
                          (:organizationId is null and :activeContractsOnly = false)
                          or exists (
                              select contract.id
                              from Contract contract
                              where contract.learner = participant.learner
                                and (:organizationId is null
                                     or contract.employer.id = :organizationId
                                     or contract.school.id = :organizationId)
                                and (:activeContractsOnly = false
                                     or contract.status = :activeContractStatus)
                          )
                      )
                    order by lesson.startsAt desc, attendance.id
                    """,
            countQuery = """
                    select count(distinct attendance)
                    from AttendanceRecord attendance
                    join attendance.lessonParticipant participant
                    join participant.lesson lesson
                    where (:startsAt is null or lesson.startsAt >= :startsAt)
                      and (:endsAtExclusive is null or lesson.startsAt < :endsAtExclusive)
                      and (:learnerId is null or participant.learner.id = :learnerId)
                      and (:instructorPersonId is null
                           or lesson.instructorPerson.id = :instructorPersonId)
                      and (
                          (:organizationId is null and :activeContractsOnly = false)
                          or exists (
                              select contract.id
                              from Contract contract
                              where contract.learner = participant.learner
                                and (:organizationId is null
                                     or contract.employer.id = :organizationId
                                     or contract.school.id = :organizationId)
                                and (:activeContractsOnly = false
                                     or contract.status = :activeContractStatus)
                          )
                      )
                    """
    )
    Page<AttendanceRecord> findAllFiltered(
            @Param("startsAt") Instant startsAt,
            @Param("endsAtExclusive") Instant endsAtExclusive,
            @Param("learnerId") String learnerId,
            @Param("organizationId") String organizationId,
            @Param("activeContractsOnly") boolean activeContractsOnly,
            @Param("activeContractStatus") ContractStatus activeContractStatus,
            @Param("instructorPersonId") String instructorPersonId,
            Pageable pageable
    );
}
