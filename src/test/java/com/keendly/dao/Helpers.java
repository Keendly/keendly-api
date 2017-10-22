package com.keendly.dao;

import static com.ninja_squad.dbsetup.Operations.*;

import com.ninja_squad.dbsetup.DbSetup;
import com.ninja_squad.dbsetup.destination.DriverManagerDestination;
import com.ninja_squad.dbsetup.operation.Operation;
import org.testcontainers.containers.PostgreSQLContainer;

public class Helpers {

    public static void executeAgainstDabase(Operation operation, PostgreSQLContainer postgresql) {
        DbSetup dbSetup = new DbSetup(
            new DriverManagerDestination(postgresql.getJdbcUrl(), postgresql.getUsername(), postgresql.getPassword()),
            operation);
        dbSetup.launch();
    }

    public static final Operation CREATE_DEFAULT_USER =
        insertInto("keendlyuser")
            .columns("id", "provider", "provider_id")
            .values(1L, "INOREADER", "123")
            .build();
}
