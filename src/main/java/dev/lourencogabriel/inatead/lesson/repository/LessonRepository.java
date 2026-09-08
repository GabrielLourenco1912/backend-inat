package dev.lourencogabriel.inatead.lesson.repository;

import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, String> {

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
