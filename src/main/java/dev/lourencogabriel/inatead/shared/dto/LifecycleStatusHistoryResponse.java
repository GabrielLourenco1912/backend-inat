package dev.lourencogabriel.inatead.shared.dto;

import dev.lourencogabriel.inatead.shared.model.LifecycleChangeReason;

import java.time.Instant;

public record LifecycleStatusHistoryResponse(
        String id,
        String previousStatus,
        String newStatus,
        LifecycleChangeReason changeReason,
        String reasonDetail,
        String changedByUserId,
        Instant changedAt
) {
}
