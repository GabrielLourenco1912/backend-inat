package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "api.notification.email.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationEmailScheduler {

    private final NotificationRecipientRepository recipientRepository;
    private final NotificationEmailDeliveryService deliveryService;
    private final Clock clock;
    private final int batchSize;

    public NotificationEmailScheduler(
            NotificationRecipientRepository recipientRepository,
            NotificationEmailDeliveryService deliveryService,
            Clock clock,
            @Value("${api.notification.email.batch-size:50}") int batchSize
    ) {
        this.recipientRepository = recipientRepository;
        this.deliveryService = deliveryService;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(fixedDelayString = "${api.notification.email.dispatch-delay:5000}")
    public void dispatchPendingEmails() {
        this.findPendingIds().forEach(this.deliveryService::deliver);
    }

    public List<String> findPendingIds() {
        return this.recipientRepository.findPendingEmailRecipientIds(
                Instant.now(this.clock),
                PageRequest.of(0, this.batchSize)
        );
    }
}
