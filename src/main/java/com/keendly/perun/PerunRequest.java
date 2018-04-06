package com.keendly.perun;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class PerunRequest {

    private String subject;
    private String sender;
    private String senderName;
    private String recipient;
    private String message;
}
