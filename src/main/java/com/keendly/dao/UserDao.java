package com.keendly.dao;

import static com.keendly.utils.DbUtils.*;

import com.keendly.adaptor.model.auth.Token;
import com.keendly.model.Provider;
import com.keendly.model.User;
import org.skife.jdbi.v2.Handle;

import java.util.Map;
import java.util.Optional;
import java.util.Date;

public class UserDao {

    private static String USER_SELECT = "select id, provider, provider_id, email, delivery_email, delivery_sender, notify_no_articles, access_token, refresh_token from keendlyuser";

    private Environment environment;

    public UserDao() {
        this(defaultEnvironment());
    }

    public UserDao(Environment environment) {
        this.environment = environment;
    }

    public User findById(Long id) {
        try (Handle handle = getDB(environment).open()) {

            Map<String, Object> map =
                handle.createQuery(USER_SELECT + " where id = :id")
                    .bind("id", id)
                    .first();

            return toUser(map);
        }
    }

    public Optional<User> findByProviderId(String providerId, Provider provider) {
        try (Handle handle = getDB(environment).open()) {

            Map<String, Object> map =
                handle.createQuery(USER_SELECT + " where provider_id = :providerId and provider = :provider")
                    .bind("providerId", providerId)
                    .bind("provider", provider)
                    .first();

            if (map == null || map.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(toUser(map));
            }
        }
    }

    private static User toUser(Map<String, Object> map) {
        return User.builder()
            .id((Long) map.get("id"))
            .provider(Provider.valueOf((String) map.get("provider")))
            .providerId((String) map.get("provider_id"))
            .email((String) map.get("email"))
            .deliveryEmail((String) map.get("delivery_email"))
            .deliverySender((String) map.get("delivery_sender"))
            .notifyNoArticles((Boolean) map.get("notify_no_articles"))
            .accessToken((String) map.get("access_token"))
            .refreshToken((String) map.get("refresh_token"))
            .build();
    }

    public void updateUser(Long id, User user) {
        try (Handle handle = getDB(environment).open()) {
            handle.createStatement("update keendlyuser "
                + "set delivery_email = :deliveryEmail, delivery_sender = :deliverySender, notify_no_articles = :notifyNoArticles "
                + "where id = :userId")
                .bind("userId", id)
                .bind("deliverySender", user.getDeliverySender())
                .bind("deliveryEmail", user.getDeliveryEmail())
                .bind("notifyNoArticles", user.getNotifyNoArticles())
                .execute();
        }
    }

    public void updateTokens(Long id, Token tokens) {
        try (Handle handle  = getDB(environment).open()) {
            handle.createStatement("update keendlyuser set access_token = :access_token, refresh_token = :refresh_token, last_login = :last_login where id = :userId")
                .bind("userId", id)
                .bind("access_token", tokens.getAccessToken())
                .bind("refresh_token", tokens.getRefreshToken())
                .bind("last_login", new Date())
                .execute();
        }
    }

    public void updateToken(Long id, String token) {
        try (Handle handle  = getDB(environment).open()) {
            handle.createStatement("update keendlyuser set access_token = :token where id = :userId")
                .bind("userId", id)
                .bind("token", token)
                .execute();
        }
    }

    public Long createUser(String externalId, String email, Provider provider) {
        Date now = new Date();
        try (Handle handle  = getDB(environment).open()) {
            Long id = nextId(handle);
            handle.createStatement("insert into keendlyuser (id, provider_id, provider, email, created, last_modified, notify_no_articles) values (:id, :providerId, :provider, :email, :now, :now, true)")
                .bind("id", id)
                .bind("providerId", externalId)
                .bind("provider", provider)
                .bind("email", email)
                .bind("now", now)
                .execute();
            return id;
        }
    }
}
