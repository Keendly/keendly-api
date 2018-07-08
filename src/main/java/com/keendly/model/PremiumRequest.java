package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PremiumRequest {
    @JsonProperty("token_id")
    private String tokenId;
    private String plainId;
    private String nonce;
}
