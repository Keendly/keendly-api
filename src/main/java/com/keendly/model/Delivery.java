package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Date;
import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Delivery {

    private Long id;
    private List<DeliveryItem> items;
    private Date deliveryDate;
    private String error;
    private Boolean manual;
    private Subscription subscription;
    private Date created;
    private String timezone;
}
