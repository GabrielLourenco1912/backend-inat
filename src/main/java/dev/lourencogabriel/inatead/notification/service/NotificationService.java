package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.model.Notification;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public NotificationDtos.Response create(
            NotificationDtos.CreateRequest request,
            String creatorUserId
    ) {
        Notification notification = prepare(request, creatorUserId);
        List<User> recipients = resolveAudience(notification, request.audience());
        return persist(notification, recipients, request.channels());
    }

    @Transactional
    public NotificationDtos.Response createForCohortRecipients(
            NotificationDtos.CreateRequest request,
            String creatorUserId,
            List<User> recipients
    ) {
        if (request.audience().type() != NotificationAudienceType.COHORT) {
            throw new BusinessRuleException(
                    "Explicit cohort recipients require a COHORT audience"
            );
        }

        Notification notification = prepare(request, creatorUserId);
        String cohortId = normalize(request.audience().targetId());
        requireTargetId(NotificationAudienceType.COHORT, cohortId);
        EntityLookup.require(cohortRepository, cohortId, "Cohort");
        notification.setAudienceType(NotificationAudienceType.COHORT);
        notification.setAudienceId(cohortId);

        LinkedHashMap<String, User> uniqueRecipients = new LinkedHashMap<>();
        for (User recipient : recipients) {
            if (recipient == null
                    || recipient.getUlid() == null
                    || recipient.getStatus() != UserAccountStatus.ACTIVE) {
                throw new BusinessRuleException(
                        "Explicit notification recipients must be active users"
                );
            }
            uniqueRecipients.put(recipient.getUlid(), recipient);
        }
        if (uniqueRecipients.isEmpty()) {
            throw new BusinessRuleException(
                    "The selected audience has no active users"
            );
        }
        return persist(
                notification,
                List.copyOf(uniqueRecipients.values()),
                request.channels()
        );
    }

    @Transactional(readOnly = true)
    public NotificationDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(notificationRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<NotificationDtos.Response> findByContext(String contextType, String contextId) {
        return notificationRepository
                .findAllByContextTypeAndContextIdOrderByCreatedAtDesc(
                        contextType.trim().toUpperCase(Locale.ROOT),
                        contextId
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationDtos.Response update(String id, NotificationDtos.UpdateRequest request) {
        Notification notification = require(id);
        if (recipientRepository.existsByNotification_IdAndDeliveryStatusNot(
                id,
                NotificationDeliveryStatus.PENDING
        )) {
            throw new BusinessRuleException(
                    "A notification that has already been processed cannot be edited"
            );
        }
        apply(notification, request);
        validate(notification, false);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public void delete(String id) {
        require(id);
        if (recipientRepository.existsByNotification_IdAndDeliveryStatusNot(
                id,
                NotificationDeliveryStatus.PENDING
        )) {
            throw new BusinessRuleException(
                    "A notification that has already been processed cannot be deleted"
            );
        }
        recipientRepository.deleteAllByNotificationId(id);
        notificationRepository.deleteById(id);
    }

    private void apply(Notification notification, NotificationDtos.CreateRequest request) {
        apply(
                notification,
                request.notificationType(),
                request.title(),
                request.message(),
                request.actionUrl(),
                request.contextType(),
                request.contextId(),
                request.payload(),
                request.priority(),
                request.scheduledAt(),
                request.expiresAt()
        );
    }

    private Notification prepare(
            NotificationDtos.CreateRequest request,
            String creatorUserId
    ) {
        Notification notification = new Notification();
        if (creatorUserId != null) {
            notification.setCreatedByUser(EntityLookup.require(
                    userRepository,
                    creatorUserId,
                    "User"
            ));
        }
        apply(notification, request);
        validate(notification, true);
        return notification;
    }

    private NotificationDtos.Response persist(
            Notification notification,
            List<User> recipients,
            Set<NotificationChannel> channels
    ) {
        Notification saved = notificationRepository.save(notification);
        createRecipients(saved, recipients, channels);
        return toResponse(saved);
    }

    private void apply(Notification notification, NotificationDtos.UpdateRequest request) {
        apply(
                notification,
                request.notificationType(),
                request.title(),
                request.message(),
                request.actionUrl(),
                request.contextType(),
                request.contextId(),
                request.payload(),
                request.priority(),
                request.scheduledAt(),
                request.expiresAt()
        );
    }

    private void apply(
            Notification notification,
            String notificationType,
            String title,
            String message,
            String actionUrl,
            String contextType,
            String contextId,
            String payload,
            NotificationPriority priority,
            Instant scheduledAt,
            Instant expiresAt
    ) {
        notification.setNotificationType(notificationType.trim().toUpperCase(Locale.ROOT));
        notification.setTitle(title.trim());
        notification.setMessage(message.trim());
        notification.setActionUrl(normalize(actionUrl));
        notification.setContextType(upperNormalize(contextType));
        notification.setContextId(normalize(contextId));
        notification.setPayload(normalizeJson(payload));
        notification.setPriority(priority);
        notification.setScheduledAt(scheduledAt);
        notification.setExpiresAt(expiresAt);
    }

    private List<User> resolveAudience(
            Notification notification,
            NotificationDtos.AudienceRequest request
    ) {
        NotificationAudienceType type = request.type();
        String targetId = normalize(request.targetId());
        notification.setAudienceType(type);

        List<User> recipients = switch (type) {
            case USER -> {
                requireTargetId(type, targetId);
                User user = EntityLookup.require(userRepository, targetId, "User");
                if (user.getStatus() != UserAccountStatus.ACTIVE) {
                    throw new BusinessRuleException(
                            "Notifications can only target active users"
                    );
                }
                notification.setAudienceId(targetId);
                yield List.of(user);
            }
            case COHORT -> {
                requireTargetId(type, targetId);
                EntityLookup.require(cohortRepository, targetId, "Cohort");
                notification.setAudienceId(targetId);
                yield userRepository.findActiveCohortRecipients(
                        targetId,
                        LocalDate.now(clock)
                );
            }
            case ALL -> {
                if (targetId != null) {
                    throw new BusinessRuleException(
                            "targetId must be omitted when audience type is ALL"
                    );
                }
                notification.setAudienceId(null);
                yield userRepository.findAllByStatusOrderByUlid(UserAccountStatus.ACTIVE);
            }
        };

        if (recipients.isEmpty()) {
            throw new BusinessRuleException(
                    "The selected audience has no active users"
            );
        }
        return recipients;
    }

    private void requireTargetId(NotificationAudienceType type, String targetId) {
        if (targetId == null) {
            throw new BusinessRuleException(
                    "targetId is required when audience type is " + type
            );
        }
    }

    private void createRecipients(
            Notification notification,
            List<User> users,
            Set<NotificationChannel> channels
    ) {
        Instant now = Instant.now(clock);
        boolean isAvailable = notification.getScheduledAt() == null
                || !notification.getScheduledAt().isAfter(now);
        List<NotificationRecipient> recipients = new ArrayList<>(users.size() * channels.size());

        for (User user : users) {
            for (NotificationChannel channel : channels) {
                NotificationRecipient recipient = new NotificationRecipient();
                recipient.setNotification(notification);
                recipient.setRecipientUser(user);
                recipient.setChannel(channel);
                if (channel == NotificationChannel.IN_APP && isAvailable) {
                    recipient.setDeliveryStatus(NotificationDeliveryStatus.DELIVERED);
                    recipient.setSentAt(now);
                    recipient.setDeliveredAt(now);
                } else {
                    recipient.setDeliveryStatus(NotificationDeliveryStatus.PENDING);
                }
                recipients.add(recipient);
            }
        }
        recipientRepository.saveAll(recipients);
    }

    private void validate(Notification notification, boolean creating) {
        boolean hasContextType = notification.getContextType() != null;
        boolean hasContextId = notification.getContextId() != null;
        if (hasContextType != hasContextId) {
            throw new BusinessRuleException(
                    "contextType and contextId must either both be provided or both be omitted"
            );
        }
        if (notification.getScheduledAt() != null
                && notification.getExpiresAt() != null
                && !notification.getExpiresAt().isAfter(notification.getScheduledAt())) {
            throw new BusinessRuleException("expiresAt must be after scheduledAt");
        }
        if (creating && notification.getExpiresAt() != null
                && !notification.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new BusinessRuleException("A notification cannot be created already expired");
        }
    }

    private String normalizeJson(String payload) {
        String normalized = normalize(payload);
        if (normalized == null) {
            return null;
        }
        try {
            return objectMapper.readTree(normalized).toString();
        } catch (Exception exception) {
            throw new BusinessRuleException("payload must contain valid JSON");
        }
    }

    private Notification require(String id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }

    private NotificationDtos.Response toResponse(Notification notification) {
        return new NotificationDtos.Response(
                notification.getId(),
                notification.getCreatedByUser() == null
                        ? null
                        : notification.getCreatedByUser().getUlid(),
                new NotificationDtos.AudienceResponse(
                        notification.getAudienceType(),
                        notification.getAudienceId()
                ),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionUrl(),
                notification.getContextType(),
                notification.getContextId(),
                notification.getPayload(),
                notification.getPriority(),
                notification.getScheduledAt(),
                notification.getExpiresAt(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upperNormalize(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
