package com.keendly.dao;

import static com.keendly.dao.Constants.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

import com.ninja_squad.dbsetup.DbSetup;
import com.ninja_squad.dbsetup.destination.DriverManagerDestination;
import com.ninja_squad.dbsetup.operation.Operation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

public class ClientDaoTest {

    private ClientDao clientDao = new ClientDao(TEST_ENVIRONMENT);

    public static final Operation DELETE_ALL =
        deleteAllFrom("client");

    @BeforeClass
    public static void createTables() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

        c.createStatement().execute(DDL.CREATE_CLIENT.sql());
        c.createStatement().execute(DDL.CREATE_SEQUENCE.sql());
        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c = DriverManager.getConnection(URL, USER, PASSWORD);

        c.createStatement().execute("drop table client");
        c.createStatement().execute("drop sequence hibernate_sequence");
        c.close();
    }

    private void execute(Operation operation) {
        DbSetup dbSetup = new DbSetup(new DriverManagerDestination(URL, USER, PASSWORD), operation);
        dbSetup.launch();
    }

    @Test
    public void given_clientExists_when_findClientSecret_returnSecret() {
        // given
        execute(
            sequenceOf(
                DELETE_ALL,
                insertInto("client")
                    .columns("id", "client_id", "client_secret", "name")
                    .values(1L, "my_id", "MY_SECRET", "test")
                    .build()
            )
        );

        // when
        Optional<String> secret = clientDao.findClientSecret("my_id");

        // then
        assertTrue(secret.isPresent());
        assertEquals("MY_SECRET", secret.get());
    }
}
