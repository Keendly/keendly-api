package com.keendly.api;

import com.jrestless.aws.gateway.GatewayFeature;
import com.jrestless.aws.gateway.handler.GatewayRequestObjectHandler;
import com.jrestless.core.filter.cors.CorsFilter;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;

public class RequestHandler extends GatewayRequestObjectHandler {

    public RequestHandler() {
        CorsFilter corsFilter = new CorsFilter.Builder()
            .allowMethodGet()
            .allowMethodPost()
            .allowMethodPut()
            .allowMethodDelete()
            .allowMethodOptions()
            .allowMethodHead()
            .allowMethod("PATCH")
            .build();

        // initialize the container with your resource configuration
        ResourceConfig config = new ResourceConfig()
            .register(GatewayFeature.class)
            .register(corsFilter)
            .packages("com.keendly.api");

        init(config);
        // start the container
        start();
    }

    public static void main(String[] args) throws IOException {
        String BASE_URI = "http://localhost:8888/";
        CorsFilter corsFilter = new CorsFilter.Builder()
            .allowMethodGet()
            .allowMethodPost()
            .allowMethodPut()
            .allowMethodDelete()
            .allowMethodOptions()
            .allowMethodHead()
            .allowMethod("PATCH")
            .build();

        ResourceConfig config = new ResourceConfig()
            .register(corsFilter)
            .packages("com.keendly.api");

        HttpServer server =
            GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), config);
        server.start();
    }
}
