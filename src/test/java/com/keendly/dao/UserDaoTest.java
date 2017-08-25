package com.keendly.dao;

import static com.keendly.dao.Constants.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

import com.keendly.model.User;
import com.ninja_squad.dbsetup.DbSetup;
import com.ninja_squad.dbsetup.destination.DriverManagerDestination;
import com.ninja_squad.dbsetup.operation.Operation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;

public class UserDaoTest {

    private final static String TABLE = "keendlyuser";

    private UserDao userDao = new UserDao(TEST_ENVIRONMENT);


    private void execute(Operation operation) {
        DbSetup dbSetup = new DbSetup(new DriverManagerDestination(URL, USER, PASSWORD), operation);
        dbSetup.launch();
    }

    @BeforeClass
    public static void createTable() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

        c.createStatement().execute(DDL.CREATE_USER.sql());
        c.createStatement().execute(DDL.CREATE_SEQUENCE.sql());

        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

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
}
