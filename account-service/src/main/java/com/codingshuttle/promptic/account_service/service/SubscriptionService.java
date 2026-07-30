package com.codingshuttle.promptic.account_service.service;


import com.codingshuttle.promptic.account_service.dto.subscription.SubscriptionResponse;
import com.codingshuttle.promptic.common_lib.dto.PlanDto;
import com.codingshuttle.promptic.common_lib.enums.SubscriptionStatus;

import java.time.Instant;
import java.util.List;

public interface SubscriptionService {
    SubscriptionResponse getCurrentSubscription();

    void activateSubscription(Long userId, Long planId, String subscriptionId, String customerId);

    void updateSubscription(String gatewaySubscriptionId, SubscriptionStatus status, Instant periodStart, Instant periodEnd, Boolean cancelAtPeriodEnd, Long planId);

    void cancelSubscription(String gatewaySubscriptionId);

    void renewSubscriptionPeriod(String subId, Instant periodStart, Instant periodEnd);

    void markSubscriptionPastDue(String subId);

    PlanDto getCurrentSubscribedPlanByUser();

    void createFreeSubscriptionForUser(Long userId);

    List<PlanDto> getAvailablePlans();
}

