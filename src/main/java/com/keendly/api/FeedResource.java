package com.keendly.api;

import com.keendly.adaptor.Adaptor;
import com.keendly.adaptor.AdaptorFactory;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.dao.DeliveryDao;
import com.keendly.dao.SubscriptionDao;
import com.keendly.dao.UserDao;
import com.keendly.model.Delivery;
import com.keendly.model.Feed;
import com.keendly.model.Subscription;
import com.keendly.model.SubscriptionItem;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/feeds")
public class FeedResource {

    private UserDao userDao = new UserDao();
    private SubscriptionDao subscriptionDao = new SubscriptionDao();
    private DeliveryDao deliveryDao = new DeliveryDao();
    private FeedMapper feedMapper = new FeedMapper();

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getFeeds(@Context SecurityContext securityContext){
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        Adaptor adaptor = AdaptorFactory.getInstance(userDao.findById(userId));

        List<ExternalFeed> subscribedFeeds = adaptor.getFeeds();
        List<SubscriptionItem> subscriptionItems = subscriptionDao.getSubscriptionItems(userId);
        List<String> feedIds = subscribedFeeds.stream().map(ExternalFeed::getFeedId).collect(Collectors.toList());
        Map<String, Delivery> lastDeliveries = deliveryDao.getLastDeliveries(userId, feedIds);
        Map<String, Integer> unreadCounts = adaptor.getUnreadCount(feedIds);

        List<Feed> feeds = new ArrayList<>();
        for (ExternalFeed subscribedFeed : subscribedFeeds) {
            List<SubscriptionItem> feedSubscriptionItems = subscriptionItems.stream()
                .filter(s -> s.getFeedId().equals(subscribedFeed.getFeedId()))
                .collect(Collectors.toList());

            List<Subscription> subscriptions = new ArrayList<>();
            if (!feedSubscriptionItems.isEmpty()){
                for (SubscriptionItem feedSubscriptionItem : feedSubscriptionItems){
                    Subscription subscription = Subscription.builder()
                        .id(feedSubscriptionItem.getSubscription().getId())
                        .time(feedSubscriptionItem.getSubscription().getTime())
                        .timezone(feedSubscriptionItem.getSubscription().getTimezone())
                        .build();
                    subscriptions.add(subscription);
                }
            }

            Delivery lastDelivery = lastDeliveries.get(subscribedFeed.getFeedId());
            Integer unreadCount = unreadCounts.get(subscribedFeed.getFeedId());

            Feed feed = feedMapper.toModel(subscribedFeed, subscriptions, lastDelivery, unreadCount);
            feeds.add(feed);

            if (adaptor.getToken().isRefreshed()) {
                userDao.updateToken(userId, adaptor.getToken().getAccessToken());
            }
        }
        return Response.ok(feeds).build();
    }

    @POST
    @Path("/markArticleRead")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response markArticleRead(@Context SecurityContext securityContext, List<String> ids) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        Adaptor adaptor = AdaptorFactory.getInstance(userDao.findById(userId));

        boolean success = adaptor.markArticleRead(ids);
        if (success) {
            if (adaptor.getToken().isRefreshed()) {
                userDao.updateToken(userId, adaptor.getToken().getAccessToken());
            }
            return Response.ok().build();
        } else {
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/markArticleUnread")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response markArticleUnread(@Context SecurityContext securityContext, List<String> ids) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        Adaptor adaptor = AdaptorFactory.getInstance(userDao.findById(userId));

        boolean success = adaptor.markArticleUnread(ids);
        if (success) {
            if (adaptor.getToken().isRefreshed()) {
                userDao.updateToken(userId, adaptor.getToken().getAccessToken());
            }
            return Response.ok().build();
        } else {
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/saveArticle")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response saveArticle(@Context SecurityContext securityContext, List<String> ids) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        Adaptor adaptor = AdaptorFactory.getInstance(userDao.findById(userId));

        boolean success = adaptor.saveArticle(ids);
        if (success) {
            if (adaptor.getToken().isRefreshed()) {
                userDao.updateToken(userId, adaptor.getToken().getAccessToken());
            }
            return Response.ok().build();
        } else {
            return Response.serverError().build();
        }
    }

    private static class FeedMapper {

        Feed toModel(ExternalFeed external, List<Subscription> subscriptions, Delivery lastDelivery, Integer unreadCount){
            return Feed.builder()
                .title(external.getTitle())
                .feedId(external.getFeedId())
                .subscriptions(subscriptions)
                .lastDelivery(lastDelivery)
                .categories(external.getCategories())
                .unreadCount(unreadCount)
                .build();
        }
    }
}
