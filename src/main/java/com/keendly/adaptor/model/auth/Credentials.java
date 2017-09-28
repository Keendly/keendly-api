package com.keendly.adaptor.model.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Credentials {

    private String authorizationCode;
    private String username;
    private String password;
}
