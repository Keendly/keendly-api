package com.keendly.dao;

import static com.ninja_squad.dbsetup.Operations.insertInto;

import com.keendly.utils.DbUtils;
import com.ninja_squad.dbsetup.operation.Operation;

public class Constants {

    public static String URL = "jdbc:postgresql://localhost:5432/postgres";
    public static String USER = "postgres";
    public static String PASSWORD = "postgres";

    public static DbUtils.Environment TEST_ENVIRONMENT = DbUtils.Environment.builder()
        .url(URL)
        .user(USER)
        .password(PASSWORD)
        .build();

    public static final Operation CREATE_DEFAULT_USER =
        insertInto("keendlyuser")
            .columns("id", "provider", "provider_id")
            .values(1L, "INOREADER", "123")
            .build();
}
