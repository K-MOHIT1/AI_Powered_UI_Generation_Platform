package com.codingshuttle.promptic.account_service.mapper;

import com.codingshuttle.promptic.account_service.dto.subscription.SubscriptionResponse;
import com.codingshuttle.promptic.account_service.entity.Plan;
import com.codingshuttle.promptic.account_service.entity.Subscription;
import com.codingshuttle.promptic.common_lib.dto.PlanDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    SubscriptionResponse toSubscriptionResponse(Subscription subscription);

    PlanDto toPlanResponse(Plan plan);
}

