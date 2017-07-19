package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

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
}
