package com.keendly.adaptor.model.auth;

import lombok.Data;

@Data
public class Credentials {

    private String authorizationCode;
    private String username;
    private String password;
}
