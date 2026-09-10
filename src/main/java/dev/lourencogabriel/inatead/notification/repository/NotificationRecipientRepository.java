package dev.lourencogabriel.inatead.notification.repository;

import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface NotificationRecipientRepository
        extends JpaRepository<NotificationRecipient, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from NotificationRecipient r where r.notification.id = :notificationId")
    int deleteAllByNotificationId(@Param("notificationId") String notificationId);

    boolean existsByNotification_IdAndDeliveryStatusNot(
            String notificationId,
            NotificationDeliveryStatus deliveryStatus
    );

    Optional<NotificationRecipient> findByIdAndRecipientUser_Ulid(String id, String userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update NotificationRecipient recipient
            set recipient.deliveryStatus = dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus.CANCELLED,
                recipient.failureReason = :reason
            where recipient.recipientUser.ulid = :userId
              and recipient.channel = dev.lourencogabriel.inatead.shared.model.NotificationChannel.EMAIL
              and recipient.deliveryStatus = dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus.PENDING
            """)
    int cancelPendingEmailsForUser(
            @Param("userId") String userId,
            @Param("reason") String reason
    );

    @Query("""
            select recipient.id
            from NotificationRecipient recipient
            where recipient.channel = dev.lourencogabriel.inatead.shared.model.NotificationChannel.EMAIL
              and recipient.deliveryStatus = dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus.PENDING
              and (recipient.notification.scheduledAt is null or recipient.notification.scheduledAt <= :now)
              and (recipient.notification.expiresAt is null or recipient.notification.expiresAt > :now)
            order by recipient.createdAt
            """)
    List<String> findPendingEmailRecipientIds(
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select recipient
            from NotificationRecipient recipient
            join fetch recipient.notification
            join fetch recipient.recipientUser
            where recipient.id = :id
            """)
    Optional<NotificationRecipient> findByIdForDelivery(@Param("id") String id);

    @Query("""
            select r
            from NotificationRecipient r
            where r.recipientUser.ulid = :userId
              and (:channel is null or r.channel = :channel)
              and (r.notification.scheduledAt is null or r.notification.scheduledAt <= :now)
              and (r.notification.expiresAt is null or r.notification.expiresAt > :now)
            order by r.createdAt desc
            """)
    Page<NotificationRecipient> findAvailableInbox(
            @Param("userId") String userId,
            @Param("channel") NotificationChannel channel,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select count(r)
            from NotificationRecipient r
            where r.recipientUser.ulid = :userId
              and r.channel = dev.lourencogabriel.inatead.shared.model.NotificationChannel.IN_APP
              and r.readAt is null
              and (r.notification.scheduledAt is null or r.notification.scheduledAt <= :now)
              and (r.notification.expiresAt is null or r.notification.expiresAt > :now)
            """)
    long countAvailableUnreadInApp(
            @Param("userId") String userId,
            @Param("now") Instant now
    );
}
