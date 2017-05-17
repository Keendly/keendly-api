package com.keendly.adaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;

import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class Adaptor {

    protected static final int MAX_ARTICLES_PER_FEED = 100;
    protected static final long TIMEOUT_IN_SECONDS = 10;

    public abstract Token login(Credentials credentials);
    public abstract ExternalUser getUser();
    public abstract List<ExternalFeed> getFeeds();
    public abstract Map<String, List<FeedEntry>> getUnread(List<String> feedIds);
    public abstract Map<String, Integer> getUnreadCount(List<String> feedIds);
    public abstract Boolean markFeedRead(List<String> feedIds, long timestamp);
    public abstract Boolean markArticleRead(List<String> articleIds);
    public abstract Boolean markArticleUnread(List<String> articleIds);
    public abstract Boolean saveArticle(List<String> articleIds);

    protected Token token;

    protected Adaptor(Token token) {
        this.token = token;
    }

    protected Adaptor() {

    }

    protected static boolean isOk(int status){
        return status == 200;
    }

    protected static boolean isUnauthorized(int status){
        if (status == 401 || status == 403){
            return true;
        }
        return false;
    }

    protected static String asText(JsonNode node, String field){
        JsonNode j = node.get(field);
        if (j != null){
            return j.asText();
        }
        return null;
    }

    protected static Date asDate(JsonNode node, String field){
        JsonNode j = node.get(field);
        if (j != null){
            Date d = new Date();
            d.setTime(j.asLong() * 1000);
            return d;
        }
        return null;
    }

    public Token getToken(){
        return token;
    }
}
