package com.keendly.dao;

import static com.keendly.dao.Helpers.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

import com.keendly.adaptor.model.auth.Token;
import com.keendly.model.Provider;
import com.keendly.model.PushSubscription;
import com.keendly.model.User;
import com.keendly.utils.DbUtils;
import com.ninja_squad.dbsetup.operation.Operation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

public class UserDaoTest {

    @ClassRule
    public static PostgreSQLContainer database = new PostgreSQLContainer();

    private final static String[] TABLES = {"keendlyuser", "pushsubscription"};

    private UserDao userDao = new UserDao(DbUtils.Environment.builder()
        .url(database.getJdbcUrl())
        .user(database.getUsername())
        .password(database.getPassword())
        .build());

    private void execute(Operation operation) {
        executeAgainstDabase(operation, database);
    }

    @BeforeClass
    public static void createTable() throws Exception {
        Connection c =
            DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());

        c.createStatement().execute(DDL.CREATE_USER.sql());
        c.createStatement().execute(DDL.CREATE_SEQUENCE.sql());
        c.createStatement().execute(DDL.CREATE_PUSH_SUBSCRIPTION.sql());

        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c =
            DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());

        Arrays.asList(TABLES).forEach(t -> {
            try {
                c.createStatement().execute("drop table " + t);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        c.createStatement().execute("drop sequence hibernate_sequence");
        c.close();
    }

    @Test
    public void when_getUser_then_return_self(){
        
    }

    @Test
    public void when_updateUser_then_updateSpecificFields() {
        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser"),
                insertInto("keendlyuser")
                    .columns("id", "provider", "provider_id")
                    .values(1L, "INOREADER", "123")
                    .build()
            )
        );

        // when
        userDao.updateUser(1L, User.builder()
            .deliveryEmail("test@kindle.com")
            .deliverySender("test@keendly.com")
            .notifyNoArticles(true)
            .build());

        // then
        User user = userDao.findById(1L);
        assertEquals("test@kindle.com", user.getDeliveryEmail());
        assertEquals("test@keendly.com", user.getDeliverySender());
        assertTrue(user.getNotifyNoArticles());
    }

    @Test
    public void given_userExistss_when_findByProviderId_then_return_returnUser(){
        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser"),
                insertInto("keendlyuser")
                    .columns("id", "provider", "provider_id")
                    .values(1L, "INOREADER", "123")
                    .build()
            )
        );

        // when
        Optional<User> user = userDao.findByProviderId("123", Provider.INOREADER);

        // then
        assertTrue(user.isPresent());
    }

    @Test
    public void given_userDoesntExist_when_findByProviderIdr_then_return_empty(){
        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser")
            )
        );

        // when
        Optional<User> user = userDao.findByProviderId("123", Provider.INOREADER);

        // then
        assertFalse(user.isPresent());
    }

    @Test
    public void when_updateTokens_setTokens() {
        String ACCESS_TOKEN = "accessToken";
        String REFRESH_TOKEN = "refreshToken";

        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser"),
                insertInto("keendlyuser")
                    .columns("id", "provider", "provider_id")
                    .values(1L, "INOREADER", "123")
                    .build()
            )
        );

        // when
        userDao.updateTokens(1L, Token.builder()
            .accessToken(ACCESS_TOKEN)
            .refreshToken(REFRESH_TOKEN)
            .build());

        // then
        User user = userDao.findById(1L);
        assertEquals(ACCESS_TOKEN, user.getAccessToken());
        assertEquals(REFRESH_TOKEN, user.getRefreshToken());
    }

    @Test
    public void when_createUser_userCreated() {
        String EXTERNAL_ID = "external id";
        String EMAIL = "blabla@email.com";
        Provider PROVIDER = Provider.INOREADER;

        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser")
            )
        );

        // when
        Long userId = userDao.createUser(EXTERNAL_ID, EMAIL, PROVIDER);

        // then
        assertNotNull(userId);
        User user = userDao.findById(userId);
        assertEquals(EXTERNAL_ID, user.getProviderId());
        assertEquals(EMAIL, user.getEmail());
        assertEquals(PROVIDER, user.getProvider());
        assertTrue(user.getNotifyNoArticles());
    }

    @Test
    public void when_setPremiumSubscriptionId_subscriptionIdSet() {
        String premiumSubscriptionId = "987654321";

        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser"),
                insertInto("keendlyuser")
                    .columns("id", "provider", "provider_id")
                    .values(1L, "INOREADER", "123")
                    .build()
            )
        );

        // when
        userDao.setPremiumSubscriptionId(1L, premiumSubscriptionId);

        // then
        User user = userDao.findById(1L);
        assertEquals(premiumSubscriptionId, user.getPremiumSubscriptionId());
    }

    @Test
    public void when_addPushSubscription_subscriptionAdded() {
        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser"),
                insertInto("keendlyuser")
                    .columns("id", "provider", "provider_id", "premium_subscription_id")
                    .values(1L, "INOREADER", "123", "9876")
                    .build()
            )
        );

        // when
        userDao.addPushSubscription(1L, PushSubscription.builder()
            .auth("auth")
            .key("key")
            .endpoint("endpoint")
            .build());

        // then
        User user = userDao.findById(1L);
        assertEquals(1, user.getPushSubscriptions().size());
        PushSubscription subscription = user.getPushSubscriptions().get(0);
        assertEquals("auth", subscription.getAuth());
        assertEquals("endpoint", subscription.getEndpoint());
    }

    @Test
    public void when_deletePushSubscription_pushSubscriptionDeleted() {
        // given
        execute(
            sequenceOf(
                deleteAllFrom("pushsubscription", "keendlyuser"),
                insertInto("keendlyuser")
                    .columns("id", "provider", "provider_id", "premium_subscription_id")
                    .values(1L, "INOREADER", "123", "9876")
                    .build()
            )
        );

        userDao.addPushSubscription(1L, PushSubscription.builder()
            .auth("auth")
            .key("key")
            .endpoint("endpoint")
            .build());

        // when
        userDao.deletePushSubscription(1L);

        // then
        assertTrue(userDao.findById(1L).getPushSubscriptions().isEmpty());
    }
}
