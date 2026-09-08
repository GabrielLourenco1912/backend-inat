package dev.lourencogabriel.inatead.notification.repository;

import dev.lourencogabriel.inatead.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findAllByContextTypeAndContextIdOrderByCreatedAtDesc(
            String contextType,
            String contextId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification notification
            set notification.expiresAt = :now,
                notification.updatedAt = :now
            where notification.notificationType = :notificationType
              and notification.contextType = :contextType
              and notification.contextId in (
                    select activity.id
                    from Activity activity
                    where activity.lesson.id = :lessonId
              )
              and (notification.expiresAt is null or notification.expiresAt > :now)
            """)
    int expireActiveActivityNotificationsForLesson(
            @Param("lessonId") String lessonId,
            @Param("notificationType") String notificationType,
            @Param("contextType") String contextType,
            @Param("now") Instant now
    );
}
