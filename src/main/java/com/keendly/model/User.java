package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class User {

    private Long id;
    private Provider provider;
    private String providerId;
    private String email;
    private String deliveryEmail;
    private String deliverySender;
    private Boolean notifyNoArticles;
    private String accessToken;
    private String refreshToken;
    private String premiumSubscriptionId;
    private Premium premium;
    private List<PushSubscription> pushSubscriptions;
    private Boolean forcePremium;
}
