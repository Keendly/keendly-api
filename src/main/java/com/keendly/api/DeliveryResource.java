package com.keendly.api;

import com.keendly.dao.DeliveryDao;
import com.keendly.model.Delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

@Path("/deliveries")
public class DeliveryResource {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryResource.class);

    private DeliveryDao deliveryDAO = new DeliveryDao();

    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getDelivery(@PathParam("id") String id) {
        Delivery d = deliveryDAO.findById(Long.parseLong(id));
        return Response.ok(d).build();
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getDeliveries(@Context SecurityContext securityContext,
        @QueryParam("subscriptionId") String subscriptionId,
        @QueryParam("page") String page,
        @QueryParam("pageSize") String pageSize) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());

        if (subscriptionId != null) {
            List<Delivery> deliveries = deliveryDAO.getSubscriptionDeliveries(userId, Long.valueOf(subscriptionId));
            return Response.ok(deliveries).build();
        } else {
            List<Delivery> deliveries =
                deliveryDAO.getDeliveries(userId, Integer.valueOf(page), Integer.valueOf(pageSize));
            return Response.ok(deliveries)
                .build();
        }
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createDelivery(Delivery delivery) {
        return Response.ok(delivery).build();
    }
}

