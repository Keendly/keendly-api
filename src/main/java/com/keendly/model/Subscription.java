package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Date;
import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Subscription {

    private Long id;
    private String time;
    private String timezone;
    private String frequency;
    private List<DeliveryItem> feeds;
    private User user;
    private Date created;
    private Boolean active;
}
