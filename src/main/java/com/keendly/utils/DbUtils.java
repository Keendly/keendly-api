package com.keendly.utils;

import static com.keendly.utils.ConfigUtils.*;

import lombok.Builder;
import lombok.Value;
import org.skife.jdbi.v2.DBI;

import java.util.HashMap;
import java.util.Map;

public class DbUtils {

    private static Map<Environment, DBI> connections = new HashMap<>();

    public static DBI getDB(Environment environment) {
        if (connections.containsKey(environment)){
            return connections.get(environment);
        }
        DBI dbi = new DBI(environment.getUrl(), environment.getUser(), environment.getPassword());
        connections.put(environment, dbi);
        return dbi;
    }

    public static Environment defaultEnvironment(){
        return Environment.builder()
            .url(parameter("DB_URL"))
            .user(parameter("DB_USER"))
            .password(parameter("DB_PASSWORD"))
            .build();
    }

    @Builder
    @Value
    public static class Environment {
        private String url;
        private String user;
        private String password;
    }
}
