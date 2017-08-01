package com.keendly.states;

import com.keendly.adaptor.model.FeedEntry;
import com.keendly.model.Delivery;
import com.keendly.model.DeliveryArticle;
import com.keendly.model.DeliveryItem;
import com.keendly.model.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Mapper {

    public static List<DeliveryItem> toDeliveryItems(Delivery delivery, Map<String, List<FeedEntry>> unread) {

        return delivery.getItems().stream()
            .map((item) ->
                DeliveryItem.builder()
                    .feedId(item.getFeedId())
                    .title(item.getTitle())
                    .id(item.getId())
                    .includeImages(item.getIncludeImages())
                    .markAsRead(item.getMarkAsRead())
                    .fullArticle(item.getFullArticle())
                    .articles(unread.containsKey(item.getFeedId())
                        ? Collections.emptyList()
                        : unread.get(item.getFeedId()).stream()
                        .map((article) -> DeliveryArticle.builder()
                            .author(article.getAuthor())
                            .id(article.getId())
                            .title(article.getTitle())
                            .timestamp(article.getPublished().getTime())
                            .url(article.getUrl())
                            .content(article.getContent())
                            .build())
                        .collect(Collectors.toList()))
                    .build())
            .filter((item) -> !item.getArticles().isEmpty())
            .collect(Collectors.toList());
    }

    public static DeliveryRequest toDeliveryRequest(Delivery delivery, S3Object s3Items, long deliveryId, User user,
        boolean dryRun) {

        return DeliveryRequest.builder()
            .email(user.getDeliveryEmail())
            .sender(user.getDeliverySender())
            .id(deliveryId)
            .userId(user.getId())
            .timestamp(System.currentTimeMillis())
            .timezone(delivery.getTimezone())
            .provider(user.getProvider())
            .s3Items(s3Items)
            .dryRun(dryRun)
            .build();
    }
}
