package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Feed {

    private String feedId;
    private String title;
    private List<Subscription> subscriptions;
    private Delivery lastDelivery;
    private List<String> categories;
    private Integer unreadCount;
}
