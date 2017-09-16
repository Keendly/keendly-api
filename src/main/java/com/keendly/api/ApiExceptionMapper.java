package com.keendly.api;

import com.keendly.adaptor.exception.ApiException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    public Response toResponse(ApiException e) {
        return Response
            .status(e.getStatus())
            .entity(e.getResponse())
            .build();
    }
}
