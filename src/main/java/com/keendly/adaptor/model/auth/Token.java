package com.keendly.adaptor.model.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Token {

    private String refreshToken;
    private String accessToken;

    private boolean refreshed;
}
