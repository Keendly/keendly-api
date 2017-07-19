package com.keendly.adaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;

import java.util.ArrayList;
import java.util.List;

public class GoogleReaderMapper {

    public static String extractArticleUrl(JsonNode node){
        if (node.get("alternate") != null){
            for (JsonNode alternate : node.get("alternate")) {
                if (alternate.get("type").asText().equals("text/html")) {
                    return alternate.get("href").asText();
                }
            }
        } else if (node.get("canonical") != null) {
            for (JsonNode canonical : node.get("canonical")) {
                return canonical.get("href").asText();
            }
        }
        return null;
    }

    public static String extractContent(JsonNode item){
        if (item.get("content") != null && item.get("content").get("content") != null){
            return item.get("content").get("content").asText();
        } else if (item.get("summary") != null && item.get("summary").get("content") != null){
            return item.get("summary").get("content").asText();
        }
        return null;
    }

    public static ExternalUser toUser(JsonNode node){
        ExternalUser user = new ExternalUser();
        user.setId(node.get("userId").asText());
        user.setUserName(node.get("userEmail").asText());
        user.setDisplayName(node.get("userName").asText());
        return user;
    }

    public static List<ExternalFeed> toFeeds(JsonNode node){
        JsonNode subs = node.get("subscriptions");
        List<ExternalFeed> externalSubscriptions = new ArrayList();
        for (JsonNode sub : subs){
            ExternalFeed externalSubscription = new ExternalFeed();
            externalSubscription.setFeedId(sub.get("id").asText());
            externalSubscription.setTitle(sub.get("title").asText());

            if (sub.get("categories") != null) {
                List<String> categories = new ArrayList<>();
                sub.get("categories").forEach(jsonNode -> categories.add(jsonNode.get("label").asText()));
                externalSubscription.setCategories(categories);
            }
            externalSubscriptions.add(externalSubscription);
        }
        return externalSubscriptions;
    }
}
