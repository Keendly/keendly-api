package com.keendly.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/users")
public class UserResource {

    @GET
    @Path("/health")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getHealthStatus() {
        return Response.ok("up and running").build();
    }
}
