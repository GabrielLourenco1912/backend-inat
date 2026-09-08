package dev.lourencogabriel.inatead.activity.repository;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
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

public interface ActivityRepository extends JpaRepository<Activity, String> {

    List<Activity> findAllByLesson_IdOrderByAvailableAtAsc(String lessonId);

    long countByLesson_IdAndStatusIn(
            String lessonId,
            Collection<ActivityStatus> statuses
    );

    @Query("""
            select activity.id
            from Activity activity
            where activity.status = :publishedStatus
              and activity.dueReminderProcessedAt is null
              and activity.dueAt > :now
              and activity.dueAt <= :windowEnd
            order by activity.dueAt, activity.id
            """)
    List<String> findDueReminderCandidateIds(
            @Param("publishedStatus") ActivityStatus publishedStatus,
            @Param("now") Instant now,
            @Param("windowEnd") Instant windowEnd,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select activity
            from Activity activity
            join fetch activity.lesson lesson
            join fetch lesson.cohort
            join fetch activity.createdByUser
            where activity.id = :id
            """)
    Optional<Activity> findByIdForDueReminder(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Activity activity
            set activity.status = :closedStatus,
                activity.updatedAt = :now
            where activity.status = :publishedStatus
              and activity.dueAt <= :now
            """)
    int closeExpiredPublishedActivities(
            @Param("now") Instant now,
            @Param("publishedStatus") ActivityStatus publishedStatus,
            @Param("closedStatus") ActivityStatus closedStatus
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Activity activity
            set activity.status = :cancelledStatus,
                activity.updatedAt = :now
            where activity.lesson.id = :lessonId
              and activity.status in :openStatuses
            """)
    int cancelOpenActivitiesByLessonId(
            @Param("lessonId") String lessonId,
            @Param("openStatuses") Collection<ActivityStatus> openStatuses,
            @Param("cancelledStatus") ActivityStatus cancelledStatus,
            @Param("now") Instant now
    );
}
