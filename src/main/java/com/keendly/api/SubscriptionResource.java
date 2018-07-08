package com.keendly.api;

import static com.keendly.premium.PremiumUtils.getPremiumStatus;

import com.keendly.dao.SubscriptionDao;
import com.keendly.dao.UserDao;
import com.keendly.model.Subscription;
import com.keendly.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.util.calendar.ZoneInfo;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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

@Path("/subscriptions")
public class SubscriptionResource {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionResource.class);
    private static final int MAX_SUBSCRIPTIONS_COUNT = 5;
    private static int MAX_FEEDS_IN_SUBSCRIPTION = 25;

    private SubscriptionDao subscriptionDao = new SubscriptionDao();
    private UserDao userDAO = new UserDao();

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getSubscriptions(@Context SecurityContext securityContext,
        @QueryParam("page") String page,
        @QueryParam("pageSize") String pageSize,
        @QueryParam("q") String query) {
        List<Subscription> subscriptions;
        if (query != null) {
            subscriptions = subscriptionDao.getDailySubscriptionsToDeliver();
        } else {
            Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
            subscriptions =
                subscriptionDao.getSubscriptions(userId, Integer.valueOf(page), Integer.valueOf(pageSize));
        }
        return Response.ok(subscriptions).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteSubscription(@PathParam("id") String id) {
        subscriptionDao.deleteSubscription(Long.parseLong(id));
        return Response.ok().build();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createSubscription(@Context SecurityContext securityContext, Subscription subscription) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());

        // validate user
        User user = userDAO.findById(userId);
        if (user.getDeliveryEmail().isEmpty()) {
            LOG.error("Delivery email not configured for user {}", userId);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Error.DELIVERY_EMAIL_NOT_CONFIGURED.asEntity())
                .build();
        }

        // check if user has active premium
        if (!getPremiumStatus(user).isActive()) {
            LOG.error("Scheduled deliveries are available for Premium users");
            return Response.status(Response.Status.PAYMENT_REQUIRED)
                .entity(Error.NO_PREMIUM.asEntity())
                .build();
        }

        // check if user can have more subscriptions
        if (subscriptionDao.getSubscriptionsCount(userId) >= MAX_SUBSCRIPTIONS_COUNT) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Error.TOO_MANY_SUBSCRIPTIONS.asEntity(MAX_SUBSCRIPTIONS_COUNT))
                .build();
        }

        // validate subscription
        if (subscription.getFeeds().size() > MAX_FEEDS_IN_SUBSCRIPTION) {
            LOG.error("Too many ({}) feeds in subscription, max: {}", subscription.getFeeds().size(),
                MAX_FEEDS_IN_SUBSCRIPTION);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Error.TOO_MANY_ITEMS.asEntity(MAX_FEEDS_IN_SUBSCRIPTION))
                .build();
        }

        Subscription toInsert = Subscription.builder()
            .timezone(ZoneInfo.getTimeZone(subscription.getTimezone()).toZoneId().getId())
            .time(subscription.getTime())
            .feeds(subscription.getFeeds())
            .build();

        Long subscriptionId = subscriptionDao.createSubscription(toInsert, userId);

        return Response.status(Response.Status.CREATED)
            .entity(Subscription.builder()
                .id(subscriptionId)
                .build())
            .build();
    }

    @PATCH
    @Path("/{id}")
    public Response updateSubscription(@PathParam("id") String id, Subscription subscription) {
        // only disabling subscriptions supported
        if (!subscription.getActive()) {
            subscriptionDao.disableSubscription(Long.parseLong(id));
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}

