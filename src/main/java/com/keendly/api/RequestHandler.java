package com.keendly.api;

import org.glassfish.jersey.server.ResourceConfig;

import com.jrestless.aws.gateway.GatewayFeature;
import com.jrestless.aws.gateway.handler.GatewayRequestObjectHandler;

public class RequestHandler extends GatewayRequestObjectHandler {
    public RequestHandler() {
        // initialize the container with your resource configuration
        ResourceConfig config = new ResourceConfig()
            .register(GatewayFeature.class)
            .packages("com.keendly.api");

        init(config);
        // start the container
        start();
    }
}
