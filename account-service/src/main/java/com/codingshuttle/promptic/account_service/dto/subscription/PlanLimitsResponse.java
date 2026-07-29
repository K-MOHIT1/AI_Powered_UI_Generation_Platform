package com.codingshuttle.promptic.account_service.dto.subscription;

public record PlanLimitsResponse(
        String planName,
        Integer maxTokensPerDay,
        Integer maxProjects,
        Boolean unlimitedAi
) {
}

