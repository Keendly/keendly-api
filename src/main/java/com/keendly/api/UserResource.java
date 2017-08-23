package com.keendly.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.keendly.dao.UserDao;
import com.keendly.model.User;

@Path("/users")
public class UserResource {

    private UserDao userDAO = new UserDao();

    @GET
    @Path("/self")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getUser(@Context SecurityContext securityContext) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());

        User user = userDAO.findById(userId);
        return Response.ok(
            User.builder()
            .id(user.getId())
            .provider(user.getProvider())
            .deliveryEmail(user.getDeliveryEmail())
            .deliverySender(user.getDeliverySender())
            .notifyNoArticles(user.getNotifyNoArticles()))
        .build();
    }
    
    @PATCH
    @Path("/self")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response updateUser(@Context SecurityContext securityContext, User user) {
        return Response.serverError().build();
    }
}
