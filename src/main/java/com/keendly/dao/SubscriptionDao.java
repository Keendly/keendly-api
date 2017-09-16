package com.keendly.dao;

import static com.keendly.utils.DbUtils.*;

import com.keendly.model.Subscription;
import com.keendly.model.SubscriptionItem;
import com.keendly.model.User;
import org.skife.jdbi.v2.Handle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                Subscription subscription = mapToSubscription(map, items, false);
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
                Subscription subscription = mapToSubscription(map, Collections.EMPTY_LIST, false);
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

    private Subscription mapToSubscription(Map<String, Object> map, List<SubscriptionItem> items, boolean includeUser) {
        Subscription.SubscriptionBuilder builder =  Subscription.builder()
            .id((Long) map.get(SUBSCRIPTION_ALIAS + "_id"))
            .created((Date) map.get(SUBSCRIPTION_ALIAS + "_created"))
            .lastModified((Date) map.get(SUBSCRIPTION_ALIAS + "_last_modified"))
            .active((Boolean) map.get(SUBSCRIPTION_ALIAS + "_active"))
            .frequency((String) map.get(SUBSCRIPTION_ALIAS + "_frequency"))
            .time((String) map.get(SUBSCRIPTION_ALIAS + "_time"))
            .timezone((String) map.get(SUBSCRIPTION_ALIAS + "_timezone"))
            .feeds(items);
        if (includeUser) {
            builder.user(User.builder()
                .id((Long) map.get(SUBSCRIPTION_ALIAS + "_user_id"))
                .build());
        }
        return builder.build();
    }

    private SubscriptionItem mapToSubscriptionItem(Map<String, Object> item, Subscription subscription) {
        return SubscriptionItem.builder()
            .feedId((String) item.get(SUBSCRIPTION_ITEM_ALIAS + "_feed_id"))
            .title((String) item.get(SUBSCRIPTION_ITEM_ALIAS + "_title"))
            .markAsRead((Boolean) item.get(SUBSCRIPTION_ITEM_ALIAS + "_mark_as_read"))
            .fullArticle((Boolean) item.get(SUBSCRIPTION_ITEM_ALIAS + "_full_article"))
            .includeImages((Boolean) item.get(SUBSCRIPTION_ITEM_ALIAS + "_with_images"))
            .subscription(subscription)
            .build();
    }

    public Long createSubscription(Subscription subscription, Long userId) {
        try (Handle handle = getDB(environment).open()) {
            handle.begin();

            Long subscriptionId = nextId(handle);
            Date now = new Date();

            handle.createStatement("insert into subscription "
                + "(id, created, last_modified, active, frequency, time, timezone, user_id, deleted) values "
                + "(:id, :now, :now, true, 'DAILY', :time, :timezone, :userId, false)")
                .bind("id", subscriptionId)
                .bind("now", now)
                .bind("time", subscription.getTime())
                .bind("timezone", subscription.getTimezone())
                .bind("userId", userId)
                .execute();

            for (SubscriptionItem item : subscription.getFeeds()) {
                Long subscriptionItemId = nextId(handle);

                handle.createStatement("insert into subscriptionitem "
                    + "(id, feed_id, full_article, mark_as_read, with_images, subscription_id, created, last_modified, title) values "
                    + "(:id, :feedId, :fullArticle, :markAsRead, :includeImages, :subscriptionId, :now, :now, :title)")
                    .bind("id", subscriptionItemId)
                    .bind("feedId", item.getFeedId())
                    .bind("fullArticle", item.getFullArticle())
                    .bind("markAsRead", item.getMarkAsRead())
                    .bind("includeImages", item.getIncludeImages())
                    .bind("subscriptionId", subscriptionId)
                    .bind("now", now)
                    .bind("title", item.getTitle())
                    .execute();
            }

            handle.commit();
            return subscriptionId;
        }
    }

    public Subscription findById(Long id) {
        try (Handle handle = getDB(environment).open()) {
            Map<String, Object> map =
                handle.createQuery(SUBSCRIPTION_SELECT + " where " + SUBSCRIPTION_ALIAS + ".id = :id")
                    .bind("id", id)
                    .first();

            List<SubscriptionItem> items = getSubscriptionItems(handle, id);
            return mapToSubscription(map, items, false);
        }
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

    public long getSubscriptionsCount(Long userId) {
        try (Handle handle = getDB(environment).open()) {
            Map<String, Object> res =  handle.createQuery("select count(*) from " + SUBSCRIPTION_TABLE + " s where s.user_id = :userId and s.active = TRUE and s.deleted = FALSE")
                .bind("userId", userId)
                .first();

            return (long) res.get("count");
        }
    }

    public List<Subscription> getDailySubscriptionsToDeliver() {
        try (Handle handle = getDB(environment).open()) {
            List<Map<String, Object>> res =  handle.createQuery("select s.id from subscription s " +
                "where s.active = TRUE and s.deleted = FALSE and s.frequency = 'DAILY' and not exists (" +
                "   select id from delivery d where d.subscription_id = s.id " +
                "       and d.created at time zone s.timezone > case " +
                "               when cast(now() at time zone s.timezone as time) > cast(s.time as time) " + // if today the scheduled hour has passed
                "               then to_timestamp(to_char(now() at time zone s.timezone,'YYYY-MM-DD ')||s.time, 'YYYY-MM-DD HH24:MI') " + // then last scheduled delivery was today
                "               else to_timestamp(to_char((now() at time zone s.timezone) - interval '1 day', 'YYYY-MM-DD ')||s.time, 'YYYY-MM-DD HH24:MI') " + // otherwise yesterday
                "       end) " +
                "       and s.created at time zone s.timezone < case " + // and was created before last scheduled delivery
                "               when cast(now() at time zone s.timezone as time) > cast(s.time as time) " +
                "               then to_timestamp(to_char(now() at time zone s.timezone,'YYYY-MM-DD ')||s.time, 'YYYY-MM-DD HH24:MI') " +
                "               else to_timestamp(to_char((now() at time zone s.timezone) - interval '1 day' ,'YYYY-MM-DD ')||s.time, 'YYYY-MM-DD HH24:MI') " +
                "       end")
                .list();

            List<Long> ids = res.stream().map(e -> (long) e.get("id")).collect(Collectors.toList());

            String q = SUBSCRIPTION_SELECT + " where " + SUBSCRIPTION_ALIAS + ".id in "
                + "(" + ids.stream().map(Object::toString).collect(Collectors.joining(",")) + ")";
            List<Map<String, Object>> subscriptionMaps = handle.createQuery(q).list();

            List<Subscription> subscriptions = new ArrayList<>();
            for (Map<String, Object> map : subscriptionMaps) {
                List<SubscriptionItem> items = getSubscriptionItems(handle, (Long) map.get(SUBSCRIPTION_ALIAS + "_id"));
                Subscription subscription = mapToSubscription(map, items, true);
                subscriptions.add(subscription);
            }

            return subscriptions;
        }

    }
}
