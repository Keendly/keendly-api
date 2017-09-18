package com.keendly.api;

import com.amazonaws.util.StringUtils;
import com.keendly.dao.UserDao;
import com.keendly.model.User;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("/users")
public class UserResource {

    private static final String[] ALLOWED_DOMAINS = {"kindle.com", "free.kindle.com", "kindle.cn", "pbsync.com"};

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
                .email(user.getEmail())
                .deliveryEmail(user.getDeliveryEmail())
                .deliverySender(user.getDeliverySender())
                .notifyNoArticles(user.getNotifyNoArticles())
                .build())
            .build();
    }
    
    @PATCH
    @Path("/self")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response updateUser(@Context SecurityContext securityContext, User user) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        User savedUser = userDAO.findById(userId);
        String deliverySender = savedUser.getDeliverySender();

        if (user.getDeliveryEmail() != null) {
            if (!validateDeliveryEmail(user.getDeliveryEmail())) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Error.WRONG_EMAIL.asEntity(StringUtils.join(", ", ALLOWED_DOMAINS)))
                    .build();
            }
            deliverySender = generateSenderEmail(user.getDeliveryEmail());
        }

        userDAO.updateUser(userId, User.builder()
            .deliveryEmail(user.getDeliveryEmail())
            .deliverySender(deliverySender)
            .notifyNoArticles(user.getNotifyNoArticles())
            .build());

        return Response.ok().build();
    }

    private boolean validateDeliveryEmail(String email){
        String[] split = email.split("\\@");
        if (split.length != 2){
            return false;
        }
        boolean valid = false;
        for (String allowedDomain : ALLOWED_DOMAINS){
            if (split[1].equals(allowedDomain)){
                valid = true;
                break;
            }
        }
        return valid;
    }

    private String generateSenderEmail(String deliveryEmail){
        String[] split = deliveryEmail.split("\\@");
        return split[0] + "@keendly.com";
    }
}
