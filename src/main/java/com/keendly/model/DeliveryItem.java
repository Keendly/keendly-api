package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeliveryItem {

    private Long id;
    private String feedId;
    private String title;
    private Boolean includeImages;
    private Boolean fullArticle;
    private Boolean markAsRead;
}
