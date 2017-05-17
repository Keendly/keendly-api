package com.keendly.api;

import com.keendly.model.Delivery;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/deliveries")
public class DeliveryResource {

    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getDelivery(@PathParam("id") String id) {

        Delivery d = Delivery.builder()
            .timezone("lala")
            .id(Long.parseLong(id))
            .build();
        return Response.ok(d).build();
    }

    @GET
    @Path("/")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getDeliveries() {
        return Response.ok("fetching all deliveries").build();
    }

    @POST
    @Path("/")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createDelivery(Delivery delivery) {
        return Response.ok(delivery).build();
    }
}
