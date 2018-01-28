package com.keendly.states;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.keendly.model.Provider;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeliveryRequest {

    private Long id;
    private Long userId;
    private Provider provider;
    private String sender;
    private String email;
    private Long timestamp;
    private S3Object s3Items;
    private boolean dryRun;
    private String timezone;
    private boolean manual;
}
