package com.keendly.dao;

import static com.keendly.dao.Helpers.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

import com.keendly.adaptor.model.auth.Token;
import com.keendly.model.Provider;
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
import java.util.Optional;

public class UserDaoTest {

    @ClassRule
    public static PostgreSQLContainer database = new PostgreSQLContainer();

    private final static String TABLE = "keendlyuser";

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

        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c =
            DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());

        c.createStatement().execute("drop table " + TABLE);
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
                deleteAllFrom(TABLE),
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
                deleteAllFrom(TABLE),
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
                deleteAllFrom(TABLE)
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
                deleteAllFrom(TABLE),
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
                deleteAllFrom(TABLE)
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
}
