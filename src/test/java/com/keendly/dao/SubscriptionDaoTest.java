package com.keendly.dao;

import static com.keendly.dao.Constants.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

import com.keendly.model.Subscription;
import com.keendly.model.SubscriptionItem;
import com.ninja_squad.dbsetup.DbSetup;
import com.ninja_squad.dbsetup.destination.DriverManagerDestination;
import com.ninja_squad.dbsetup.operation.Operation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class SubscriptionDaoTest {

    private SubscriptionDao subscriptionDao = new SubscriptionDao(TEST_ENVIRONMENT);

    public static final Operation DELETE_ALL =
        deleteAllFrom("subscriptionitem", "subscription", "keendlyuser");

    @BeforeClass
    public static void createTables() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

        c.createStatement().execute(DDL.CREATE_USER.sql());
        c.createStatement().execute(DDL.CREATE_SUBSCRIPTION.sql());
        c.createStatement().execute(DDL.CREATE_SUBSCRIPTION_ITEM.sql());
        c.createStatement().execute(DDL.CREATE_SEQUENCE.sql());

        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

        c.createStatement().execute("drop table subscriptionitem");
        c.createStatement().execute("drop table subscription");
        c.createStatement().execute("drop table keendlyuser");
        c.createStatement().execute("drop sequence hibernate_sequence");

        c.close();
    }

    private void execute(Operation operation) {
        DbSetup dbSetup = new DbSetup(new DriverManagerDestination(URL, USER, PASSWORD), operation);
        dbSetup.launch();
    }

    private static String format(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(date);
    }

    @Test
    public void when_getSubscriptionItems_then_returnSubscription() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build(),
                insertInto("subscriptionitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "subscription_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.259", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 2L)
                    .build()
            )
        );

        // when
        List<SubscriptionItem> items = subscriptionDao.getSubscriptionItems(1L);

        // then
        assertEquals(1, items.size());
        SubscriptionItem item = items.get(0);
        assertEquals(1L, item.getId().longValue());
        assertEquals("2016-05-19 23:59:00.274", format(item.getCreated()));
        assertEquals("2016-05-19 23:59:00.259", format(item.getLastModified()));
        assertEquals("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", item.getFeedId());
        assertEquals("Giant Robots Smashing Into Other Giant Robots", item.getTitle());
        assertEquals(true, item.getIncludeImages());
        assertEquals(true, item.getFullArticle());
        assertEquals(true, item.getMarkAsRead());

        Subscription subscription = item.getSubscription();
        assertEquals(2L, subscription.getId().longValue());
        assertEquals("2016-05-21 01:17:17.739", format(subscription.getCreated()));
        assertEquals("2016-05-22 01:17:17.739", format(subscription.getLastModified()));
        assertEquals(true, subscription.getActive());
        assertEquals("DAILY", subscription.getFrequency());
        assertEquals("00:00", subscription.getTime());
        assertEquals("Europe/Madrid", subscription.getTimezone());
    }

    @Test
    public void given_userDoesntExist_when_getSubscriptionItems_returnEmptyList() {
        // when
        List<SubscriptionItem> items = subscriptionDao.getSubscriptionItems(999L);

        // then
        assertTrue(items.isEmpty());
    }

    @Test
    public void given_subscriptionDeleted_when_getSubscriptionItems_dontReturn() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-19 23:59:00.259", "2016-05-19 23:59:00.259", true, "DAILY", "16:00", "Europe/Istanbul", "1", true)
                    .build(),
                insertInto("subscriptionitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "subscription_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.274", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 1L)
                    .build()
            )
        );

        // when
        List<SubscriptionItem> items = subscriptionDao.getSubscriptionItems(1L);

        // then
        assertTrue(items.isEmpty());
    }

    @Test
    public void given_subscriptionNotActive_when_getSubscriptionItems_dontReturn() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-19 23:59:00.259", "2016-05-19 23:59:00.259", false, "DAILY", "16:00", "Europe/Istanbul", "1", false)
                    .build(),
                insertInto("subscriptionitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "subscription_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.274", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 1L)
                    .build()
            )
        );

        // when
        List<SubscriptionItem> items = subscriptionDao.getSubscriptionItems(1L);

        // then
        assertTrue(items.isEmpty());
    }

    @Test
    public void when_getSubscription_then_returnSubscriptionItems() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build(),
                insertInto("subscriptionitem")
                    .columns("id", "created", "last_modified", "feed_id", "title", "full_article", "mark_as_read", "with_images", "subscription_id")
                    .values(1L, "2016-05-19 23:59:00.274", "2016-05-19 23:59:00.259", "feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", "Giant Robots Smashing Into Other Giant Robots", true, true, true, 2L)
                    .build()
            )
        );

        // when
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 1, 5);

        // then
        assertEquals(1, subscriptions.size());
        Subscription subscription = subscriptions.get(0);
        assertEquals(2L, subscription.getId().longValue());
        assertEquals("2016-05-21 01:17:17.739", format(subscription.getCreated()));
        assertEquals("2016-05-22 01:17:17.739", format(subscription.getLastModified()));
        assertEquals(true, subscription.getActive());
        assertEquals("DAILY", subscription.getFrequency());
        assertEquals("00:00", subscription.getTime());
        assertEquals("Europe/Madrid", subscription.getTimezone());

        assertEquals(1, subscription.getFeeds().size());
        SubscriptionItem item = subscription.getFeeds().get(0);
        assertEquals(1L, item.getId().longValue());
        assertEquals("2016-05-19 23:59:00.274", format(item.getCreated()));
        assertEquals("2016-05-19 23:59:00.259", format(item.getLastModified()));
        assertEquals("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", item.getFeedId());
        assertEquals("Giant Robots Smashing Into Other Giant Robots", item.getTitle());
        assertEquals(true, item.getIncludeImages());
        assertEquals(true, item.getFullArticle());
        assertEquals(true, item.getMarkAsRead());
    }

    @Test
    public void given_subscriptionActiveAndDeleted_when_getSubscriptions_then_isNotReturned(){
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", true)
                    .build()
            )
        );

        // when
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 1, 5);

        // then
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    public void given_subscriptionNotActiveAndDeleted_when_getSubscriptions_then_isNotReturned(){
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", false, "DAILY", "00:00", "Europe/Madrid", "1", true)
                    .build()
            )
        );

        // when
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 1, 5);

        // then
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    public void given_subscriptionNotActiveAndNotDeleted_when_getSubscriptions_then_isNotReturned(){
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", false, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );

        // when
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 1, 5);

        // then
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    public void given_twoPages_when_getSubscriptionsFirstPage_then_returnFirstPage(){
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build(),
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );

        // when
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 1, 1);

        // then
        assertEquals(1, subscriptions.size());
        Subscription subscription = subscriptions.get(0);
        assertEquals(2L, subscription.getId().longValue());
    }

    @Test
    public void given_twoPages_when_getSubscriptionsSecondPage_then_returnSecondPage(){
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build(),
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );

        // when
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 2, 1);

        // then
        assertEquals(1, subscriptions.size());
        Subscription subscription = subscriptions.get(0);
        assertEquals(1L, subscription.getId().longValue());
    }

    @Test
    public void given_twoPages_when_getSubscriptionsThirdPage_then_returnEmptyList(){
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build(),
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );

        // when
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 3, 1);

        // then
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    public void when_deleteSubscription_then_setDeleted() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );

        // when
        subscriptionDao.deleteSubscription(1L);

        // then
        List<Subscription> subscriptions = subscriptionDao.getSubscriptions(1L, 1, 1);
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    public void given_noSubscriptions_when_getSubscriptionsCount_then_returnZero() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER
            )
        );

        // when
        long count = subscriptionDao.getSubscriptionsCount(1L);

        // then
        assertEquals(0, count);
    }

    @Test
    public void given_subscriptionsExist_when_getSubscriptionsCount_then_returnCount() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build(),
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );

        // when
        long count = subscriptionDao.getSubscriptionsCount(1L);

        // then
        assertEquals(2, count);
    }

    @Test
    public void given_subscriptionsNotActive_when_getSubscriptionsCount_then_dontCount() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", false, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );

        // when
        long count = subscriptionDao.getSubscriptionsCount(1L);

        // then
        assertEquals(0, count);
    }

    @Test
    public void given_subscriptionsDeleted_when_getSubscriptionsCount_then_dontCount() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(1L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", true)
                    .build()
            )
        );

        // when
        long count = subscriptionDao.getSubscriptionsCount(1L);

        // then
        assertEquals(0, count);
    }

    @Test
    public void given_subscription_when_createSubscription_then_create() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER
            )
        );
        Subscription subscription = Subscription.builder()
            .time("01:00")
            .timezone("Europe/Madrid")
            .feeds(Arrays.asList(
                SubscriptionItem.builder()
                    .feedId("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots")
                    .title("Giant Robots Smashing Into Other Giant Robots")
                    .includeImages(true)
                    .markAsRead(true)
                    .fullArticle(true)
                    .build()
            ))
            .build();

        // when
        Long subscriptionId = subscriptionDao.createSubscription(subscription, 1L);

        // then
        Subscription inserted = subscriptionDao.findById(subscriptionId);
        assertEquals(subscriptionId, inserted.getId());
        assertEquals("DAILY", inserted.getFrequency());
        assertTrue(inserted.getActive());
        SubscriptionItem insertedItem = inserted.getFeeds().get(0);
        assertEquals("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", insertedItem.getFeedId());
        assertEquals("Giant Robots Smashing Into Other Giant Robots", insertedItem.getTitle());
        assertTrue(insertedItem.getIncludeImages());
        assertTrue(insertedItem.getMarkAsRead());
        assertTrue(insertedItem.getFullArticle());
    }
}
