package com.keendly.veles;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class VelesRequest {

    private String subject;
    private String sender;
    private String senderName;
    private String recipient;
    private String message;
}
