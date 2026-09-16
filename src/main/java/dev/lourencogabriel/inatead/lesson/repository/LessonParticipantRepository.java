package dev.lourencogabriel.inatead.lesson.repository;

import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface LessonParticipantRepository extends JpaRepository<LessonParticipant, String> {

    boolean existsByLesson_IdAndLearner_Id(String lessonId, String learnerId);

    boolean existsByLesson_Id(String lessonId);

    List<LessonParticipant> findAllByLearner_IdAndStatusAndLesson_DeliveryModeAndLesson_StatusIn(
            String learnerId,
            ParticipationStatus status,
            DeliveryMode deliveryMode,
            Collection<LessonStatus> lessonStatuses
    );

    @EntityGraph(attributePaths = {"learner", "sourceEnrollment"})
    List<LessonParticipant> findAllByLesson_Id(String lessonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select participant
            from LessonParticipant participant
            where participant.lesson.id = :lessonId
              and participant.status = :status
            order by participant.id
            """)
    List<LessonParticipant> findAllForOnlineAttendance(
            @Param("lessonId") String lessonId,
            @Param("status") ParticipationStatus status
    );

    Optional<LessonParticipant> findByLesson_IdAndLearner_Id(String lessonId, String learnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select participant
            from LessonParticipant participant
            join fetch participant.lesson
            where participant.lesson.id = :lessonId
              and participant.learner.id = :learnerId
            """)
    Optional<LessonParticipant> findByLessonAndLearnerForUpdate(
            @Param("lessonId") String lessonId,
            @Param("learnerId") String learnerId
    );

    @Query("""
            select (count(participant) > 0)
            from LessonParticipant participant
            where participant.learner.id = :learnerId
              and participant.lesson.id <> :lessonId
              and participant.status <> :cancelledParticipation
              and participant.lesson.status <> :cancelledLesson
              and participant.lesson.startsAt < :endsAt
              and participant.lesson.endsAt > :startsAt
            """)
    boolean existsLearnerScheduleConflict(
            @Param("learnerId") String learnerId,
            @Param("lessonId") String lessonId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("cancelledParticipation") ParticipationStatus cancelledParticipation,
            @Param("cancelledLesson") LessonStatus cancelledLesson
    );

    @Query("""
            select (count(currentParticipant) > 0)
            from LessonParticipant currentParticipant
            where currentParticipant.lesson.id = :lessonId
              and currentParticipant.status <> :cancelledParticipation
              and exists (
                  select conflictingParticipant.id
                  from LessonParticipant conflictingParticipant
                  where conflictingParticipant.learner = currentParticipant.learner
                    and conflictingParticipant.lesson.id <> :lessonId
                    and conflictingParticipant.status <> :cancelledParticipation
                    and conflictingParticipant.lesson.status <> :cancelledLesson
                    and conflictingParticipant.lesson.startsAt < :endsAt
                    and conflictingParticipant.lesson.endsAt > :startsAt
              )
            """)
    boolean existsScheduleConflictForLessonParticipants(
            @Param("lessonId") String lessonId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("cancelledParticipation") ParticipationStatus cancelledParticipation,
            @Param("cancelledLesson") LessonStatus cancelledLesson
    );

    boolean existsBySourceEnrollment_Id(String sourceEnrollmentId);

    List<LessonParticipant> findAllBySourceEnrollment_IdAndStatusNotAndLesson_StatusIn(
            String sourceEnrollmentId,
            ParticipationStatus excludedStatus,
            Collection<LessonStatus> lessonStatuses
    );

    boolean existsByLearner_IdAndLesson_InstructorPerson_Id(
            String learnerId,
            String instructorPersonId
    );
}
