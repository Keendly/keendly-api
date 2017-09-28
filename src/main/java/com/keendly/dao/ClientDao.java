package com.keendly.dao;

import static com.keendly.utils.DbUtils.*;

import com.keendly.utils.DbUtils;
import org.skife.jdbi.v2.Handle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClientDao {

    private DbUtils.Environment environment;

    public ClientDao() {
        this(defaultEnvironment());
    }

    public ClientDao(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> findClientSecret(String clientId) {
        try (Handle handle  = getDB(environment).open()) {

            List<Map<String, Object>> map =
                handle.createQuery("select client_secret from client where client_id = :clientId")
                    .bind("clientId", clientId)
                    .list();

            if (map.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of((String) map.get(0).get("client_secret"));
            }
        }
    }
}
