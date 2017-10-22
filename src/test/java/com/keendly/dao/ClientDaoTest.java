package com.keendly.dao;

import static com.keendly.dao.Helpers.*;
import static com.ninja_squad.dbsetup.Operations.*;
import static org.junit.Assert.*;

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

public class ClientDaoTest {

    @ClassRule
    public static PostgreSQLContainer database = new PostgreSQLContainer();

    private ClientDao clientDao = new ClientDao(DbUtils.Environment.builder()
        .url(database.getJdbcUrl())
        .user(database.getUsername())
        .password(database.getPassword())
        .build());

    public static final Operation DELETE_ALL =
        deleteAllFrom("client");

    @BeforeClass
    public static void createTables() throws Exception {
        Connection c =
            DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());

        c.createStatement().execute(DDL.CREATE_CLIENT.sql());
        c.createStatement().execute(DDL.CREATE_SEQUENCE.sql());
        c.close();
    }

    @AfterClass
    public static void dropTables() throws Exception {
        Connection c =
            DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());

        c.createStatement().execute("drop table client");
        c.createStatement().execute("drop sequence hibernate_sequence");
        c.close();
    }

    private void execute(Operation operation) {
        executeAgainstDabase(operation, database);
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
