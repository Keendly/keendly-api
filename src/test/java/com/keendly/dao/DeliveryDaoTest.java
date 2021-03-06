package com.keendly.dao;

import static com.keendly.dao.Helpers.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

import com.keendly.model.Delivery;
import com.keendly.model.DeliveryItem;
import com.keendly.model.Subscription;
import com.keendly.utils.DbUtils;
import com.ninja_squad.dbsetup.operation.Operation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class DeliveryDaoTest {

    @ClassRule
    public static PostgreSQLContainer database = new PostgreSQLContainer();

    private DeliveryDao deliveryDao = new DeliveryDao(DbUtils.Environment.builder()
        .url(database.getJdbcUrl())
        .user(database.getUsername())
        .password(database.getPassword())
        .build());

    private static String[] TABLES = {"deliveryitem", "delivery", "subscription", "keendlyuser"};

    public static final Operation DELETE_ALL = deleteAllFrom(TABLES);

    private void execute(Operation operation) {
        executeAgainstDabase(operation, database);
    }

    private static String format(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(date);
    }

    @BeforeClass
    public static void createTables() throws Exception {
        Connection c =
            DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());

        c.createStatement().execute(DDL.CREATE_USER.sql());
        c.createStatement().execute(DDL.CREATE_SUBSCRIPTION.sql());
        c.createStatement().execute(DDL.CREATE_DELIVERY.sql());
        c.createStatement().execute(DDL.CREATE_DELIVERY_ITEM.sql());
        c.createStatement().execute(DDL.CREATE_SEQUENCE.sql());

        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c =
            DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());

        for (String table : TABLES){
            c.createStatement().execute("drop table " + table);
        }
        c.createStatement().execute("drop sequence hibernate_sequence");

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
    public void given_deliveryError_when_findById_then_returnError() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual", "errordescription")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, true, "BOOM!")
                    .build()
            )
        );

        // when
        Delivery delivery = deliveryDao.findById(2L);

        // then
        assertNotNull(delivery);
        assertEquals("BOOM!", delivery.getError());
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
    public void given_delivery_when_createDelivery_then_create() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER
            )
        );
        Delivery delivery = Delivery.builder()
            .manual(true)
            .items(Arrays.asList(
                DeliveryItem.builder()
                    .feedId("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots")
                    .title("Giant Robots Smashing Into Other Giant Robots")
                    .includeImages(true)
                    .markAsRead(true)
                    .fullArticle(true)
                    .build()
            ))
            .build();

        // when
        Long deliveryId = deliveryDao.createDelivery(delivery, 1L);

        // then
        Delivery inserted = deliveryDao.findById(deliveryId);
        assertEquals(deliveryId, inserted.getId());
        DeliveryItem insertedItem = inserted.getItems().get(0);
        assertEquals("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots", insertedItem.getFeedId());
        assertEquals("Giant Robots Smashing Into Other Giant Robots", insertedItem.getTitle());
        assertTrue(insertedItem.getIncludeImages());
        assertTrue(insertedItem.getMarkAsRead());
        assertTrue(insertedItem.getFullArticle());
    }

    @Test
    public void given_delivery_when_createDeliveryForSubscription_then_setSubscriptionId() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );
        Delivery delivery = Delivery.builder()
            .manual(false)
            .subscription(Subscription.builder()
                .id(2L)
                .build())
            .items(Arrays.asList(
                DeliveryItem.builder()
                    .feedId("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots")
                    .title("Giant Robots Smashing Into Other Giant Robots")
                    .includeImages(true)
                    .markAsRead(true)
                    .fullArticle(true)
                    .build()
            ))
            .build();

        // when
        Long deliveryId = deliveryDao.createDelivery(delivery, 1L);

        // then
        Delivery inserted = deliveryDao.findById(deliveryId);
        assertEquals(2L, inserted.getSubscription().getId().longValue());
    }

    @Test
    public void given_delivery_when_createDeliveryWithError_then_setErrorDescription() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("subscription")
                    .columns("id", "created", "last_modified", "active", "frequency", "time", "timezone", "user_id", "deleted")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:17.739", true, "DAILY", "00:00", "Europe/Madrid", "1", false)
                    .build()
            )
        );
        Delivery delivery = Delivery.builder()
            .manual(false)
            .subscription(Subscription.builder()
                .id(2L)
                .build())
            .error("BOOM!")
            .items(Arrays.asList(
                DeliveryItem.builder()
                    .feedId("feed/http://feeds.feedburner.com/GiantRobotsSmashingIntoOtherGiantRobots")
                    .title("Giant Robots Smashing Into Other Giant Robots")
                    .includeImages(true)
                    .markAsRead(true)
                    .fullArticle(true)
                    .build()
            ))
            .build();

        // when
        Long deliveryId = deliveryDao.createDelivery(delivery, 1L);

        // then
        Delivery inserted = deliveryDao.findById(deliveryId);
        assertEquals("BOOM!", inserted.getError());
    }

    @Test
    public void given_delivered_when_setDeliveryFinished_then_deliveryUpdated() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, false)
                    .build()
            )
        );

        // when
        deliveryDao.setDeliveryFinished(2L, new Date(), null);

        // then
        Delivery delivery = deliveryDao.findById(2L);
        assertNotNull(delivery.getDeliveryDate());
        assertNull(delivery.getError());
    }

    @Test
    public void given_deliveryError_when_setDeliveryFinished_then_deliveryUpdated() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                CREATE_DEFAULT_USER,
                insertInto("delivery")
                    .columns("id", "created", "last_modified", "user_id", "manual")
                    .values(2L, "2016-05-21 01:17:17.739", "2016-05-22 01:17:11.739", 1L, false)
                    .build()
            )
        );

        // when
        deliveryDao.setDeliveryFinished(2L, null, "BOOOM!");

        // then
        Delivery delivery = deliveryDao.findById(2L);
        assertNotNull(delivery.getError());
        assertNull(delivery.getDeliveryDate());
    }
}
