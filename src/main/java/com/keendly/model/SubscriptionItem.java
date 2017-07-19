package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Date;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SubscriptionItem {

    private Long id;
    private Date created;
    private Date lastModified;
    private String feedId;
    private String title;
    private Boolean includeImages;
    private Boolean fullArticle;
    private Boolean markAsRead;
    private Subscription subscription;
}
