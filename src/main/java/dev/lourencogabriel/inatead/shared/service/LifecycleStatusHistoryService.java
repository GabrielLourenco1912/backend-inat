package dev.lourencogabriel.inatead.shared.service;

import dev.lourencogabriel.inatead.shared.dto.LifecycleStatusHistoryResponse;
import dev.lourencogabriel.inatead.shared.model.LifecycleAggregateType;
import dev.lourencogabriel.inatead.shared.model.LifecycleChangeReason;
import dev.lourencogabriel.inatead.shared.model.LifecycleStatusHistory;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.shared.repository.LifecycleStatusHistoryRepository;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LifecycleStatusHistoryService {

    private final LifecycleStatusHistoryRepository repository;
    private final UserRepository userRepository;

    public void append(
            LifecycleAggregateType aggregateType,
            String aggregateId,
            Enum<?> previousStatus,
            Enum<?> newStatus,
            LifecycleChangeReason changeReason,
            String reasonDetail,
            String actorUserId,
            Instant changedAt
    ) {
        LifecycleStatusHistory history = new LifecycleStatusHistory();
        history.setAggregateType(aggregateType);
        history.setAggregateId(aggregateId);
        history.setPreviousStatus(previousStatus == null ? null : previousStatus.name());
        history.setNewStatus(newStatus.name());
        history.setChangeReason(changeReason);
        history.setReasonDetail(normalize(reasonDetail));
        history.setChangedByUser(actorUserId == null
                ? null
                : EntityLookup.require(userRepository, actorUserId, "Status change user"));
        history.setChangedAt(changedAt);
        repository.save(history);
    }

    public List<LifecycleStatusHistoryResponse> find(
            LifecycleAggregateType aggregateType,
            String aggregateId
    ) {
        return repository
                .findAllByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
                        aggregateType,
                        aggregateId
                ).stream()
                .map(history -> new LifecycleStatusHistoryResponse(
                        history.getId(),
                        history.getPreviousStatus(),
                        history.getNewStatus(),
                        history.getChangeReason(),
                        history.getReasonDetail(),
                        history.getChangedByUser() == null
                                ? null
                                : history.getChangedByUser().getUlid(),
                        history.getChangedAt()
                ))
                .toList();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
