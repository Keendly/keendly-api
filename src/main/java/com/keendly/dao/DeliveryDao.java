package com.keendly.dao;

import static com.keendly.utils.DbUtils.*;

import com.keendly.model.Delivery;
import com.keendly.model.DeliveryItem;
import org.skife.jdbi.v2.Handle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeliveryDao {

    private static String DELIVERY_SELECT = "select id, created, last_modified, date, errordescription from delivery";

    private Environment environment;

    public DeliveryDao() {
        this(defaultEnvironment());
    }

    public DeliveryDao(Environment environment) {
        this.environment = environment;
    }

    public Delivery findById(Long id) {
        try (Handle handle = getDB(environment).open()) {
            Map<String, Object> map =
                handle.createQuery(DELIVERY_SELECT + " where id = :id")
                    .bind("id", id)
                    .first();

            List<DeliveryItem> items = getDeliveryItems(handle, id);
            return mapToDelivery(map, items);
        }
    }

    public List<Delivery> getSubscriptionDeliveries(Long userId, Long subscriptionId) {
        try (Handle handle = getDB(environment).open()) {
            List<Map<String, Object>> mapList =
                handle.createQuery(DELIVERY_SELECT
                    + " where user_id = :userId and subscription_id = :subscriptionId order by id desc")
                    .bind("userId", userId)
                    .bind("subscriptionId", subscriptionId)
                    .setMaxRows(100)
                    .list();

            return mapToDeliveryList(handle, mapList);
        }
    }

    public List<Delivery> getDeliveries(Long userId, int page, int pageSize){
        try (Handle handle = getDB(environment).open()) {

            List<Map<String, Object>> mapList =
                handle.createQuery(DELIVERY_SELECT
                    + " where user_id = :userId order by id desc OFFSET :offset")
                    .bind("userId", userId)
                    .bind("offset", pageSize * (page - 1))
                    .setMaxRows(pageSize)
                    .list();

            return mapToDeliveryList(handle, mapList);
        }
    }

    private List<DeliveryItem> getDeliveryItems(Handle handle, Long deliveryId) {
        List<Map<String, Object>> itemsMaps =
            handle.createQuery("select id, feed_id, title, full_article, mark_as_read, with_images "
                + "from deliveryitem where delivery_id = :deliveryId")
                .bind("deliveryId", deliveryId)
                .list();

        List<DeliveryItem> items = new ArrayList<>();
        for (Map<String, Object> item : itemsMaps) {
            DeliveryItem i = DeliveryItem.builder()
                .feedId((String) item.get("feed_id"))
                .id((Long) item.get("id"))
                .title((String) item.get("title"))
                .markAsRead((Boolean) item.get("mark_as_read"))
                .fullArticle((Boolean) item.get("full_article"))
                .includeImages((Boolean) item.get("with_images"))
                .build();

            items.add(i);
        }

        return items;
    }

    private Delivery mapToDelivery(Map<String, Object> map, List<DeliveryItem> items) {
        return Delivery.builder()
            .id((Long) map.get("id"))
            .created((Date) map.get("created"))
            .lastModified((Date) map.get("last_modified"))
            .deliveryDate((Date) map.get("date"))
            .items(items)
            .timezone((String) map.get("timezone"))
            .build();
    }

    private List<Delivery> mapToDeliveryList(Handle handle, List<Map<String, Object>> deliveryMaps){
        List<Delivery> deliveries = new ArrayList<>();
        for (Map<String, Object> map : deliveryMaps) {
            List<DeliveryItem> items = getDeliveryItems(handle, (Long) map.get("id"));

            Delivery delivery = mapToDelivery(map, items);

            deliveries.add(delivery);
        }
        return deliveries;
    }

    public Map<String, Delivery> getLastDeliveries(Long userId, List<String> feedIds) {
        Map<String, Delivery> ret = new HashMap<>();
        try (Handle handle = getDB(environment).open()) {
            String query = new StringBuilder()
                .append("select ")
                .append("di.feed_id, d.id, d.created, d.last_modified, d.date, d.errordescription ")
                .append("from deliveryitem di join delivery d on d.id = di.delivery_id ")
                .append("where d.user_id = :userId ")
                .append("and (di.feed_id, d.date) IN ")
                .append("(select di.feed_id, max(d.date) from delivery d join deliveryitem di on di.delivery_id = d.id where d.user_id = :userId group by di.feed_id)")
                .toString();

            String feedIdsString = String.join(",", feedIds.stream().map(feedId -> "'" + feedId + "'").collect(Collectors.toList()));
            List<Map<String, Object>> list =
                handle.createQuery(String.format(query, feedIdsString))
                    .bind("userId", userId)
                    .list();

            for (Map<String, Object> item : list) {
                Delivery d = mapToDelivery(item, Collections.EMPTY_LIST);
                ret.put((String) item.get("feed_id"), d);
            }
        }
        return ret;
    }

    public Delivery getLastDelivery(Long userId, String feedId) {
        try (Handle handle = getDB(environment).open()) {
            String query = new StringBuilder()
                .append("select ")
                .append("d.id, d.created, d.last_modified, d.date, d.errordescription ")
                .append("from deliveryitem di join delivery d on d.id = di.delivery_id ")
                .append("where d.user_id = :userId and di.feed_id = :feedId ")
                .append("and d.date is not null order by d.date desc")
                .toString();

            Map<String, Object> map =
                handle.createQuery(query)
                    .bind("userId", userId)
                    .bind("feedId", feedId)
                    .first();

            if (map == null || map.isEmpty()) {
                return null;
            }
            return mapToDelivery(map, Collections.EMPTY_LIST);
        }
    }
}
