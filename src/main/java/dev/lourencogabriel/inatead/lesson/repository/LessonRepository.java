package dev.lourencogabriel.inatead.lesson.repository;

import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, String> {

    boolean existsByInstructorPerson_Id(String personId);

    List<Lesson> findAllByInstructorPerson_IdOrderByStartsAtDesc(String instructorPersonId);

    @Query("""
            select distinct l
            from Lesson l
            join LessonParticipant p on p.lesson = l
            where p.learner.person.id = :personId
            order by l.startsAt desc
            """)
    List<Lesson> findAllForLearnerPerson(@Param("personId") String personId);

    boolean existsByCohort_IdAndStartsAt(String cohortId, Instant startsAt);

    boolean existsByCohort_IdAndStartsAtAndIdNot(String cohortId, Instant startsAt, String id);

    boolean existsByCohort_Id(String cohortId);

    List<Lesson> findAllByCohort_Id(String cohortId);

    boolean existsByCohort_IdAndStatusIn(
            String cohortId,
            Collection<LessonStatus> statuses
    );

    List<Lesson> findAllByCohort_IdAndStatusIn(
            String cohortId,
            Collection<LessonStatus> statuses
    );

    @Query("""
            select lesson.id
            from Lesson lesson
            where lesson.deliveryMode = :deliveryMode
              and lesson.status <> :cancelledLesson
              and lesson.endsAt <= :now
              and exists (
                  select participant.id
                  from LessonParticipant participant
                  where participant.lesson = lesson
                    and participant.status = :expectedParticipation
                    and not exists (
                        select attendance.id
                        from AttendanceRecord attendance
                        where attendance.lessonParticipant = participant
                    )
              )
            order by lesson.endsAt, lesson.id
            """)
    List<String> findOnlineAttendanceCandidateIds(
            @Param("now") Instant now,
            @Param("deliveryMode") DeliveryMode deliveryMode,
            @Param("cancelledLesson") LessonStatus cancelledLesson,
            @Param("expectedParticipation") ParticipationStatus expectedParticipation,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select lesson
            from Lesson lesson
            join fetch lesson.instructorPerson
            where lesson.id = :lessonId
            """)
    Optional<Lesson> findByIdForOnlineAttendance(@Param("lessonId") String lessonId);

    @Query("""
            select (count(lesson) > 0)
            from Lesson lesson
            where lesson.cohort.id = :cohortId
              and (lesson.startsAt < :startsAt or lesson.endsAt > :endsAt)
            """)
    boolean existsOutsideCohortPeriod(
            @Param("cohortId") String cohortId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Query("""
            select (count(l) > 0)
            from Lesson l
            where l.cohort.id = :cohortId
              and l.status <> :cancelled
              and (:currentId is null or l.id <> :currentId)
              and l.startsAt < :endsAt
              and l.endsAt > :startsAt
            """)
    boolean existsCohortScheduleConflict(
            @Param("cohortId") String cohortId,
            @Param("currentId") String currentId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("cancelled") LessonStatus cancelled
    );

    @Query("""
            select (count(l) > 0)
            from Lesson l
            where l.instructorPerson.id = :instructorId
              and l.status <> :cancelled
              and (:currentId is null or l.id <> :currentId)
              and l.startsAt < :endsAt
              and l.endsAt > :startsAt
            """)
    boolean existsInstructorScheduleConflict(
            @Param("instructorId") String instructorId,
            @Param("currentId") String currentId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("cancelled") LessonStatus cancelled
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Lesson lesson
            set lesson.status = :completedStatus,
                lesson.updatedAt = :now
            where lesson.status in :openStatuses
              and lesson.endsAt <= :now
            """)
    int completeElapsedLessons(
            @Param("now") Instant now,
            @Param("openStatuses") Collection<LessonStatus> openStatuses,
            @Param("completedStatus") LessonStatus completedStatus
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Lesson lesson
            set lesson.status = :inProgressStatus,
                lesson.updatedAt = :now
            where lesson.status = :scheduledStatus
              and lesson.startsAt <= :now
              and lesson.endsAt > :now
            """)
    int startCurrentLessons(
            @Param("now") Instant now,
            @Param("scheduledStatus") LessonStatus scheduledStatus,
            @Param("inProgressStatus") LessonStatus inProgressStatus
    );
}
