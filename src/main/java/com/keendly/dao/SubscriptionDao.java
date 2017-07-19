package com.keendly.dao;

import static com.keendly.utils.DbUtils.*;

import com.keendly.model.Subscription;
import com.keendly.model.SubscriptionItem;
import org.skife.jdbi.v2.Handle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class SubscriptionDao {

    private static final String SUBSCRIPTION_TABLE = "subscription";
    private static final String SUBSCRIPTION_ITEM_TABLE = "subscriptionitem";
    private static final String SUBSCRIPTION_ALIAS = "s";
    private static final String SUBSCRIPTION_ITEM_ALIAS = "si";

    private static final String[] SUBSCRIPTION_FIELDS = {
        column(SUBSCRIPTION_ALIAS, "id"),
        column(SUBSCRIPTION_ALIAS, "created"),
        column(SUBSCRIPTION_ALIAS, "last_modified"),
        column(SUBSCRIPTION_ALIAS, "active"),
        column(SUBSCRIPTION_ALIAS, "frequency"),
        column(SUBSCRIPTION_ALIAS, "time"),
        column(SUBSCRIPTION_ALIAS, "timezone"),
        column(SUBSCRIPTION_ALIAS, "user_id")
    };

    private static final String[] SUBSCRIPTION_ITEM_FIELDS = {
        column(SUBSCRIPTION_ITEM_ALIAS, "id"),
        column(SUBSCRIPTION_ITEM_ALIAS, "created"),
        column(SUBSCRIPTION_ITEM_ALIAS, "last_modified"),
        column(SUBSCRIPTION_ITEM_ALIAS, "feed_id"),
        column(SUBSCRIPTION_ITEM_ALIAS, "title"),
        column(SUBSCRIPTION_ITEM_ALIAS, "with_images"),
        column(SUBSCRIPTION_ITEM_ALIAS, "full_article"),
        column(SUBSCRIPTION_ITEM_ALIAS, "mark_as_read")
    };

    private static final String SUBSCRIPTION_SELECT =
        "select " + String.join(" ,", SUBSCRIPTION_FIELDS) + " from " + SUBSCRIPTION_TABLE + " " + SUBSCRIPTION_ALIAS;

    private static final String SUBSCRIPTION_ITEM_SELECT =
        "select " + String.join(",", SUBSCRIPTION_ITEM_FIELDS) + " from " + SUBSCRIPTION_ITEM_TABLE + " " + SUBSCRIPTION_ITEM_ALIAS;

    private Environment environment;

    private static String column(String alias, String column) {
        return alias + "." + column + " as " + alias + "_" + column;
    }

    public SubscriptionDao(){
        this(defaultEnvironment());
    }

    public SubscriptionDao(Environment environment) {
        this.environment = environment;
    }

    public List<Subscription> getSubscriptions(Long userId, int page, int pageSize) {
        try (Handle handle = getDB(environment).open()) {
            List<Map<String, Object>> subscriptionMaps =
                handle.createQuery(SUBSCRIPTION_SELECT
                    + " where user_id = :userId and active = TRUE and deleted = FALSE order by id desc OFFSET :offset")
                    .bind("userId", userId)
                    .bind("offset", pageSize * (page - 1))
                    .setMaxRows(pageSize)
                    .list();

            List<Subscription> subscriptions = new ArrayList<>();
            for (Map<String, Object> map : subscriptionMaps) {
                List<SubscriptionItem> items = getSubscriptionItems(handle, (Long) map.get(SUBSCRIPTION_ALIAS + "_id"));
                Subscription subscription = mapToSubscription(map, items);
                subscriptions.add(subscription);
            }
            return subscriptions;
        }
    }

    public List<SubscriptionItem> getSubscriptionItems(Long userId) {
        try (Handle handle = getDB(environment).open()) {
            String query = new StringBuilder()
                .append("select ")
                .append(String.join(",", SUBSCRIPTION_ITEM_FIELDS))
                .append(",")
                .append(String.join(",", SUBSCRIPTION_FIELDS))
                .append(" from ")
                .append(SUBSCRIPTION_TABLE)
                .append(" ")
                .append(SUBSCRIPTION_ALIAS)
                .append(" join ")
                .append(SUBSCRIPTION_ITEM_TABLE)
                .append(" ")
                .append(SUBSCRIPTION_ITEM_ALIAS)
                .append(" on ")
                .append(SUBSCRIPTION_ALIAS)
                .append(".id = ")
                .append(SUBSCRIPTION_ITEM_ALIAS)
                .append(".subscription_id")
                .append(" where s.active = TRUE and s.deleted = FALSE and s.user_id = :userId")
                .toString();

            List<Map<String, Object>> subscriptionItemMaps =
                handle.createQuery(query)
                    .bind("userId", userId)
                    .list();

            List<SubscriptionItem> subscriptionItems = new ArrayList<>();
            for (Map<String, Object> map : subscriptionItemMaps) {
                Subscription subscription = mapToSubscription(map, Collections.EMPTY_LIST);
                SubscriptionItem i = mapToSubscriptionItem(map, subscription);
                subscriptionItems.add(i);
            }
            return subscriptionItems;
        }
    }

    private List<SubscriptionItem> getSubscriptionItems(Handle handle, Long subscriptionId) {
        List<Map<String, Object>> itemsMaps =
            handle.createQuery(SUBSCRIPTION_ITEM_SELECT
                + " where subscription_id = :subscriptionId order by id desc")
                .bind("subscriptionId", subscriptionId)
                .list();

        List<SubscriptionItem> items = new ArrayList<>();
        for (Map<String, Object> item : itemsMaps) {
            SubscriptionItem i = mapToSubscriptionItem(item, null);
            items.add(i);
        }

        return items;
    }

    private Subscription mapToSubscription(Map<String, Object> map, List<SubscriptionItem> items) {
        return Subscription.builder()
            .id((Long) map.get(SUBSCRIPTION_ALIAS + "_id"))
            .created((Date) map.get(SUBSCRIPTION_ALIAS + "_created"))
            .lastModified((Date) map.get(SUBSCRIPTION_ALIAS + "_last_modified"))
            .active((Boolean) map.get(SUBSCRIPTION_ALIAS + "_active"))
            .frequency((String) map.get(SUBSCRIPTION_ALIAS + "_frequency"))
            .time((String) map.get(SUBSCRIPTION_ALIAS + "_time"))
            .timezone((String) map.get(SUBSCRIPTION_ALIAS + "_timezone"))
            .feeds(items)
            .build();
    }

    private SubscriptionItem mapToSubscriptionItem(Map<String, Object> item, Subscription subscription) {
        return SubscriptionItem.builder()
            .feedId((String) item.get(SUBSCRIPTION_ITEM_ALIAS + "_feed_id"))
            .id((Long) item.get(SUBSCRIPTION_ITEM_ALIAS + "_id"))
            .title((String) item.get(SUBSCRIPTION_ITEM_ALIAS + "_title"))
            .markAsRead((Boolean) item.get(SUBSCRIPTION_ITEM_ALIAS + "_mark_as_read"))
            .fullArticle((Boolean) item.get(SUBSCRIPTION_ITEM_ALIAS + "_full_article"))
            .includeImages((Boolean) item.get(SUBSCRIPTION_ITEM_ALIAS + "_with_images"))
            .created((Date) item.get(SUBSCRIPTION_ITEM_ALIAS + "_created"))
            .lastModified((Date) item.get(SUBSCRIPTION_ITEM_ALIAS + "_last_modified"))
            .subscription(subscription)
            .build();
    }

    public void createSubscription(Subscription subscription) {
        // TODO
    }

    public Subscription findById(Long id) {
        // TODO
        return null;
    }

    public void deleteSubscription(Long id) {
        try (Handle handle = getDB(environment).open()) {
            String update = new StringBuilder()
                .append("update ")
                .append(SUBSCRIPTION_TABLE)
                .append(" set deleted=true")
                .append(" where id = :subscriptionId")
                .toString();
            handle.createStatement(update)
                .bind("subscriptionId", id)
                .execute();
        }
    }

    public void updateSubscription(Subscription subscription) {
        // TODO
    }
}
