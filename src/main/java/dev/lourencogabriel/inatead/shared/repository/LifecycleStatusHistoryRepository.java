package dev.lourencogabriel.inatead.shared.repository;

import dev.lourencogabriel.inatead.shared.model.LifecycleAggregateType;
import dev.lourencogabriel.inatead.shared.model.LifecycleStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LifecycleStatusHistoryRepository
        extends JpaRepository<LifecycleStatusHistory, String> {

    List<LifecycleStatusHistory> findAllByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
            LifecycleAggregateType aggregateType,
            String aggregateId
    );
}
