package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.notification.dto.NotificationRecipientDtos;
import dev.lourencogabriel.inatead.notification.model.Notification;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationRecipientService {

    private final NotificationRecipientRepository recipientRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public NotificationRecipientDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationRecipientDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(recipientRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationRecipientDtos.Response> findInbox(
            String userId,
            NotificationChannel channel,
            Pageable pageable
    ) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return PageResponse.from(
                recipientRepository.findAvailableInbox(
                        userId,
                        channel,
                        Instant.now(),
                        pageable
                ).map(this::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public NotificationRecipientDtos.UnreadCount unreadCount(String userId) {
        return new NotificationRecipientDtos.UnreadCount(
                recipientRepository.countAvailableUnreadInApp(userId, Instant.now())
        );
    }

    @Transactional
    public NotificationRecipientDtos.Response updateDelivery(
            String id,
            NotificationRecipientDtos.DeliveryRequest request
    ) {
        NotificationRecipient recipient = require(id);
        NotificationDeliveryStatus current = recipient.getDeliveryStatus();
        NotificationDeliveryStatus target = request.deliveryStatus();
        if (current == target) {
            return toResponse(recipient);
        }
        if (!allowedTargets(current).contains(target)) {
            throw new BusinessRuleException(
                    "Invalid delivery transition from " + current + " to " + target
            );
        }

        Instant now = Instant.now();
        switch (target) {
            case PENDING -> {
                recipient.setSentAt(null);
                recipient.setDeliveredAt(null);
                recipient.setFailureReason(null);
            }
            case SENT -> {
                recipient.setSentAt(now);
                recipient.setDeliveredAt(null);
                recipient.setFailureReason(null);
            }
            case DELIVERED -> {
                if (recipient.getSentAt() == null) {
                    recipient.setSentAt(now);
                }
                recipient.setDeliveredAt(now);
                recipient.setFailureReason(null);
            }
            case FAILED -> {
                String reason = normalize(request.failureReason());
                if (reason == null) {
                    throw new BusinessRuleException(
                            "failureReason is required for a failed delivery"
                    );
                }
                recipient.setDeliveredAt(null);
                recipient.setFailureReason(reason);
            }
        }
        recipient.setDeliveryStatus(target);
        return toResponse(recipientRepository.save(recipient));
    }

    @Transactional
    public NotificationRecipientDtos.Response markRead(String id, String userId) {
        NotificationRecipient recipient = recipientRepository
                .findByIdAndRecipientUser_Ulid(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification recipient", id));
        Instant now = Instant.now();
        if (recipient.getNotification().getScheduledAt() != null
                && recipient.getNotification().getScheduledAt().isAfter(now)) {
            throw new BusinessRuleException("A scheduled notification cannot be read early");
        }
        if (recipient.getNotification().getExpiresAt() != null
                && !recipient.getNotification().getExpiresAt().isAfter(now)) {
            throw new BusinessRuleException("An expired notification cannot be marked as read");
        }
        if (recipient.getChannel() == NotificationChannel.IN_APP
                && recipient.getDeliveryStatus() == NotificationDeliveryStatus.PENDING) {
            recipient.setDeliveryStatus(NotificationDeliveryStatus.DELIVERED);
            recipient.setSentAt(now);
            recipient.setDeliveredAt(now);
        }
        if (recipient.getReadAt() == null) {
            recipient.setReadAt(now);
            recipientRepository.save(recipient);
        }
        return toResponse(recipient);
    }

    private Set<NotificationDeliveryStatus> allowedTargets(
            NotificationDeliveryStatus current
    ) {
        return switch (current) {
            case PENDING -> EnumSet.of(
                    NotificationDeliveryStatus.SENT,
                    NotificationDeliveryStatus.DELIVERED,
                    NotificationDeliveryStatus.FAILED
            );
            case SENT -> EnumSet.of(
                    NotificationDeliveryStatus.DELIVERED,
                    NotificationDeliveryStatus.FAILED
            );
            case DELIVERED, CANCELLED -> EnumSet.noneOf(NotificationDeliveryStatus.class);
            case FAILED -> EnumSet.of(NotificationDeliveryStatus.PENDING);
        };
    }

    private NotificationRecipient require(String id) {
        return recipientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification recipient", id));
    }

    private NotificationRecipientDtos.Response toResponse(NotificationRecipient recipient) {
        Notification notification = recipient.getNotification();
        return new NotificationRecipientDtos.Response(
                recipient.getId(),
                notification.getId(),
                recipient.getRecipientUser().getUlid(),
                recipient.getChannel(),
                recipient.getDeliveryStatus(),
                recipient.getSentAt(),
                recipient.getDeliveredAt(),
                recipient.getReadAt(),
                recipient.getFailureReason(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionUrl(),
                notification.getContextType(),
                notification.getContextId(),
                notification.getPriority(),
                notification.getScheduledAt(),
                notification.getExpiresAt(),
                recipient.getCreatedAt(),
                recipient.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
