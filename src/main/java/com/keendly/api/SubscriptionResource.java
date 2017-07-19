package com.keendly.api;

import com.keendly.dao.SubscriptionDao;
import com.keendly.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

@Path("/subscriptions")
public class SubscriptionResource {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionResource.class);

    private SubscriptionDao subscriptionDao = new SubscriptionDao();

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getSubscriptions(@Context SecurityContext securityContext,
        @QueryParam("page") String page,
        @QueryParam("pageSize") String pageSize) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());

        List<Subscription> subscriptions =
            subscriptionDao.getSubscriptions(userId, Integer.valueOf(page), Integer.valueOf(pageSize));
        return Response.ok(subscriptions)
            .build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteSubscription(@PathParam("id") String id) {
        subscriptionDao.deleteSubscription(Long.parseLong(id));
        return Response.ok().build();
    }
}

