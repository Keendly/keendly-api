package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Date;
import java.util.List;

@Builder
@Value
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Delivery {

    private Long id;
    private List<DeliveryItem> items;
    private Date deliveryDate;
    private String error;
    private Boolean manual;
    private Subscription subscription;
    private Date created;
    private Date lastModified;
    private String timezone;
}
