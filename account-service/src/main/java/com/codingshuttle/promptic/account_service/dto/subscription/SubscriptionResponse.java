package com.codingshuttle.promptic.account_service.dto.subscription;

import com.codingshuttle.promptic.common_lib.dto.PlanDto;

import java.time.Instant;

public record SubscriptionResponse(
        PlanDto plan,
        String status,
        Instant currentPeriodEnd,
        Long tokensUsedThisCycle
) {
}

