package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeliveryArticle {

    private String id;
    private String url;
    private String title;
    private Long timestamp;
    private String author;
    private String content;
}
