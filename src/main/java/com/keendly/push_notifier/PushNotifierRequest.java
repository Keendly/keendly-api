package com.keendly.push_notifier;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class PushNotifierRequest {

    private Long userId;
    private String title;
    private String body;
}
