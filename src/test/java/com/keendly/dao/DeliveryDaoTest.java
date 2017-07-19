package com.keendly.dao;

import static com.keendly.dao.Constants.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

import com.keendly.adaptor.inoreader.InoreaderAdaptor;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.auth.Token;
import com.keendly.model.Delivery;
import com.keendly.model.DeliveryItem;
import com.keendly.model.Feed;
import com.keendly.model.Subscription;
import com.keendly.model.SubscriptionItem;
import com.keendly.model.User;
import com.keendly.utils.DbUtils;
import com.ninja_squad.dbsetup.DbSetup;
import com.ninja_squad.dbsetup.destination.DriverManagerDestination;
import com.ninja_squad.dbsetup.operation.Operation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeliveryDaoTest {

    private DeliveryDao deliveryDao = new DeliveryDao(TEST_ENVIRONMENT);

    private static String[] TABLES = {"deliveryitem", "delivery", "subscription", "keendlyuser"};

    public static final Operation DELETE_ALL =
        deleteAllFrom(TABLES);

    private void execute(Operation operation) {
        DbSetup dbSetup = new DbSetup(new DriverManagerDestination(URL, USER, PASSWORD), operation);
        dbSetup.launch();
    }

    private static String format(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(date);
    }

    @BeforeClass
    public static void createTables() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

        c.createStatement().execute(DDL.CREATE_USER.sql());
        c.createStatement().execute(DDL.CREATE_SUBSCRIPTION.sql());
        c.createStatement().execute(DDL.CREATE_DELIVERY.sql());
        c.createStatement().execute(DDL.CREATE_DELIVERY_ITEM.sql());

        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

        for (String table : TABLES){
            c.createStatement().execute("drop table " + table);
        }

        c.close();
    }

    @Test
    public void given_deliveryExists_when_findById_then_return() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, true)
                    .build(),
                insertInto("deliveryitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "delivery_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.259", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 2L)
                    .build()
            )
        );

        // when
        Delivery delivery = deliveryDao.findById(2L);

        // then
        assertNotNull(delivery);
        assertEquals(2L, delivery.getId().longValue());
        assertEquals("2016-05-21 01:17:17.739", format(delivery.getCreated()));
        assertEquals("2016-05-22 01:17:11.739", format(delivery.getLastModified()));

        assertEquals(1, delivery.getItems().size());
        DeliveryItem item = delivery.getItems().get(0);
        assertEquals(1L, item.getId().longValue());
        assertEquals("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", item.getFeedId());
        assertEquals("Giant Robots Smashing Into Other Giant Robots", item.getTitle());
        assertTrue(item.getMarkAsRead());
        assertTrue(item.getFullArticle());
        assertTrue(item.getIncludeImages());
    }

    @Test
    public void given_deliveryFromSubscription_when_findById_then_returnSubscription() {

    }

    @Test
    public void given_deliveryDoesntExist_when_findById_then_returnNull() {

    }

    @Test
    public void given_noDeliveries_when_getSubscriptionDeliveries_then_returnEmptyList() {

    }

    @Test
    public void given_deliveriesExist_when_getSubscriptionDeliveries_then_return() {

    }

    @Test
    public void given_userDoesntExist_when_getSubscriptionDeliveries_then_returnEmptyList() {

    }

    @Test
    public void given_userDoesntExist_when_getDeliveries_then_returnEmptyList() {

    }

    @Test
    public void given_allOnFirstPage_when_getDeliveries_then_returnAll() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, true)
                    .build(),
                insertInto("deliveryitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "delivery_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.259", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 2L)
                    .build()
            )
        );

        // when
        List<Delivery> deliveries = deliveryDao.getDeliveries(1L, 1, 5);

        // then
        assertEquals(1, deliveries.size());
    }

    @Test
    public void given_moreThanOnePage_when_getDeliveries_then_returnOnlyFirstPage() {

    }

    @Test
    public void given_secondPageRequested_when_getDeliveries_then_returnSecondPage() {

    }

    @Test
    public void given_lastPageRequested_when_getDeliveries_then_returnLastPage() {

    }

    @Test
    public void given_noContentForRequestedPage_when_getDeliveries_then_returnEmpty() {

    }

    @Test
    public void given_deliveriesExist_when_getLastDelivery_then_returnLastOne() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual", "date")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, true, "2016-05-22 01:17:11.739")
                    .build(),
                insertInto("deliveryitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "delivery_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.259", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 2L)
                    .build()
            )
        );

        // when
        Delivery delivery = deliveryDao.getLastDelivery(1L, "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots");

        // then
        assertNotNull(delivery);
        assertEquals(2L, delivery.getId().longValue());
        assertEquals("2016-05-22 01:17:11.739", format(delivery.getDeliveryDate()));
    }

    @Test
    public void given_noDeliveries_when_getLastDelivery_then_returnNull() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual", "date")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, true, "2016-05-22 01:17:11.739")
                    .build(),
                insertInto("deliveryitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "delivery_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.259", "some_other_feed", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 2L)
                    .build()
            )
        );

        // when
        Delivery delivery = deliveryDao.getLastDelivery(1L, "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots");

        // then
        assertNull(delivery);
    }

    @Test
    public void given_deliveryDateNull_when_getLastDelivery_then_returnNull() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, true)
                    .build(),
                insertInto("deliveryitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "delivery_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.259", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 2L)
                    .build()
            )
        );

        // when
        Delivery delivery = deliveryDao.getLastDelivery(1L, "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots");

        // then
        assertNull(delivery);
    }

    @Test
    public void test(){
        DbUtils.Environment e = DbUtils.Environment.builder()
            .url("pupaurl")
            .password("cyce")
            .user("atam")
            .build();

        SubscriptionDao subscriptionDao  = new SubscriptionDao(e);
        UserDao userDao = new UserDao(e);
        User user = userDao.findById(2L);

        Token token = Token.builder()
            .accessToken(user.getAccessToken())
            .refreshToken(user.getRefreshToken())
            .build();

        InoreaderAdaptor inoreaderAdaptor = new InoreaderAdaptor(token);


        DeliveryDao deliveryDao = new DeliveryDao(e);

        long start = System.currentTimeMillis();
        List<ExternalFeed> subscribedFeeds = inoreaderAdaptor.getFeeds();
        System.out.println(System.currentTimeMillis() - start);
        List<SubscriptionItem> subscriptionItems = subscriptionDao.getSubscriptionItems(2L);
        Map<String,Delivery> lastDeliveries = deliveryDao.getLastDeliveries(2L,
            subscribedFeeds.stream().map(ExternalFeed::getFeedId).collect(Collectors.toList()));

        System.out.println(System.currentTimeMillis() - start);
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
            Feed feed = Feed.builder()
                .title(subscribedFeed.getTitle())
                .feedId(subscribedFeed.getFeedId())
                .subscriptions(subscriptions)
                .lastDelivery(lastDelivery)
                .build();

            feeds.add(feed);

            // TODO refresh token after implement userDAO
        }

        System.out.println(System.currentTimeMillis() - start);
        String a = "a";
    }
}
