package com.keendly.adaptor.feedly;

import static com.keendly.adaptor.feedly.FeedlyAdaptor.FeedlyParam.*;
import static com.keendly.utils.ConfigUtils.*;
import static com.keendly.utils.JsonUtils.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.keendly.adaptor.Adaptor;
import com.keendly.adaptor.exception.ApiException;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedlyAdaptor extends Adaptor {

    enum FeedlyParam {
        URL,
        CLIENT_ID,
        CLIENT_SECRET,
        REDIRECT_URL
    }

    protected Map<FeedlyAdaptor.FeedlyParam, String> config;

    private JerseyClient client = JerseyClientBuilder.createClient();

    public FeedlyAdaptor(Token token) {
        this(token, defaultConfig());
    }

    public FeedlyAdaptor(Credentials credentials) {
        this(credentials, defaultConfig());
    }

    public FeedlyAdaptor(Token token, Map<FeedlyAdaptor.FeedlyParam, String> config){
        super(token);
        this.config = config;
    }

    public FeedlyAdaptor(Credentials credentials, Map<FeedlyAdaptor.FeedlyParam, String> config){
        super();
        this.config = config;
        this.token = login(credentials);
    }

    private static Map<FeedlyParam, String> defaultConfig(){
        Map<FeedlyParam, String> config = new HashMap<>();
        config.put(URL, parameter("FEEDLY_URL"));
        config.put(CLIENT_ID, parameter("FEEDLY_CLIENT_ID"));
        config.put(CLIENT_SECRET, parameter("FEEDLY_CLIENT_SECRET"));
        config.put(REDIRECT_URL, parameter("FEEDLY_REDIRECT_URI"));

        return config;
    }

    @Override
    public Token login(Credentials credentials) {
        JsonNode json = JsonNodeFactory.instance.objectNode()
            .put("grant_type", "authorization_code")
            .put("client_id", config.get(CLIENT_ID))
            .put("client_secret", config.get(CLIENT_SECRET))
            .put("code", credentials.getAuthorizationCode())
            .put("redirect_uri", config.get(REDIRECT_URL));

        Response response = client.target(config.get(URL) + "/auth/token")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(Entity.json(json));

        if (isOk(response.getStatus())) {
            JsonNode node = asJson(response);
            String refreshToken = node.get("refresh_token").asText();
            String accessToken = node.get("access_token").asText();

            return Token.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    private Response get(String url, Map<String, String> params, boolean refreshIfNeeded) {
        WebTarget target = client.target(config.get(URL) + url);
        for (Map.Entry<String, String> param : params.entrySet()) {
            target = target.queryParam(param.getKey(), param.getValue());
        }

        Response response = target.request()
            .header("Authorization", "Bearer " + token.getAccessToken())
            .get();

        if (isOk(response.getStatus())) {
            return response;
        } else if (refreshIfNeeded && isUnauthorized(response.getStatus())) {
            String refreshedToken = refreshAccessToken();
            token.setAccessToken(refreshedToken);
            token.setRefreshed(true);
            return get(url, params, false);
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    protected Response get(String url) {
        return get(url, Collections.EMPTY_MAP, true);
    }

    private String refreshAccessToken(){
        JsonNode json = JsonNodeFactory.instance.objectNode()
            .put("grant_type", "refresh_token")
            .put("client_id", config.get(CLIENT_ID))
            .put("client_secret", config.get(CLIENT_SECRET))
            .put("refresh_token", token.getRefreshToken());

        Response response = client.target(config.get(URL) + "/auth/token")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(Entity.json(json));

        if (isOk(response.getStatus())) {
            JsonNode node = asJson(response);
            return node.get("access_token").asText();
        } else {
            String body = response.readEntity(String.class);
            throw new ApiException(response.getStatus(), body);
        }
    }

    @Override
    public ExternalUser getUser() {
        Response response = get("/profile");
        JsonNode node = asJson(response);
        ExternalUser user = new ExternalUser();
        user.setId(node.get("id").asText());
        user.setUserName(node.get("email").asText());
        user.setDisplayName(node.get("fullName").asText());
        return user;
    }

    @Override
    public List<ExternalFeed> getFeeds() {
        Response response = get("/subscriptions");
        JsonNode node = asJson(response);
        List<ExternalFeed> externalSubscriptions = new ArrayList();
        if (node.isArray()) {
            for (JsonNode item : node) {
                externalSubscriptions.add(mapFromJson(item));
            }
        } else {
            externalSubscriptions.add(mapFromJson(node));
        }
        return externalSubscriptions;
    }

    private ExternalFeed mapFromJson(JsonNode json){
        ExternalFeed externalSubscription = new ExternalFeed();
        externalSubscription.setFeedId(json.get("id").asText());
        externalSubscription.setTitle(json.get("title").asText());
        if (json.get("categories") != null) {
            List<String> categories = new ArrayList<>();
            json.get("categories").forEach(jsonNode -> categories.add(jsonNode.get("label").asText()));
            externalSubscription.setCategories(categories);
        }
        return externalSubscription;
    }

    @Override
    public Map<String, List<FeedEntry>> getUnread(List<String> feedIds) {
        Response response = get("/markers/counts");
        JsonNode json = asJson(response);
        Map<String, List<FeedEntry>> unreads = new HashMap<String, List<FeedEntry>>();
        for(JsonNode feedCount : json.get("unreadcounts")) {
            String feedId = feedCount.get("id").asText();
            if (feedIds.contains(feedId)) {
                int count = feedCount.get("count").asInt();
                List<FeedEntry> unread = doGetUnread(feedId, count, null);
                unreads.put(feedId, unread);
            }
        }

        return unreads;
    }

    private List<FeedEntry> doGetUnread(String feedId, int unreadCount, String continuation) {
        int count = unreadCount > MAX_ARTICLES_PER_FEED ? MAX_ARTICLES_PER_FEED : unreadCount; // TODO inform user
        String url = "/streams/" + urlEncode(feedId) + "/contents";
        url = continuation == null ? url : url + "?continuation=" + continuation;
        Response response = get(url);
        JsonNode jsonResponse = asJson(response);
        JsonNode items = jsonResponse.get("items");
        if (items == null){
            return Collections.emptyList();
        }
        List<FeedEntry> entries = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.get("unread").asBoolean()) {
                String articleUrl = null;
                String originIdString = null;
                JsonNode originId = item.get("originId");
                if (originId != null) {
                    originIdString = item.get("originId").asText();
                }
                if (isURL(originIdString)) {
                    articleUrl = originIdString;
                } else {
                    for (JsonNode alternate : item.get("alternate")) {
                        if (alternate.get("type").asText().equals("text/html")) {
                            articleUrl = alternate.get("href").asText();
                        }
                    }
                }
                if (articleUrl != null) {
                    FeedEntry entry = new FeedEntry();
                    entry.setId(asText(item, "id"));
                    entry.setUrl(articleUrl);
                    entry.setTitle(asText(item, "title"));
                    entry.setAuthor(asText(item, "author"));
                    entry.setPublished(asDate(item, "published"));
                    entry.setContent(extractContent(item));
                    entries.add(entry);
                }
                if (entries.size() >= count) {
                    break;
                }
            }
        }
        List<FeedEntry> ret = new ArrayList();
        ret.addAll(entries);
        if (ret.size() < count){
            JsonNode continuationNode = jsonResponse.get("continuation");
            boolean hasContinuation = continuationNode != null;
            if (hasContinuation){
                List<FeedEntry> nextPage =
                    doGetUnread(feedId, count - ret.size(), continuationNode.asText());
                ret.addAll(nextPage);
                return ret;
            }
        }
        return ret;
    }

    private static String urlEncode(String s){
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isURL(String s){
        if (s == null) {
            return false;
        }
        try {
            new URL(s);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private String extractContent(JsonNode item){
        if (item.get("content") != null && item.get("content").get("content") != null){
            return item.get("content").get("content").asText();
        } else if (item.get("summary") != null && item.get("summary").get("content") != null){
            return item.get("summary").get("content").asText();
        }
        return null;
    }

    @Override
    public Map<String, Integer> getUnreadCount(List<String> feedIds) {
        Response response = get("/markers/counts");
        JsonNode json = asJson(response);
        Map<String, Integer> unreadCount = new HashMap<>();
        for (JsonNode feedCount : json.get("unreadcounts")) {
            String feedId = feedCount.get("id").asText();
            if (feedIds.contains(feedId)) {
                int count = feedCount.get("count").asInt();
                unreadCount.put(feedId, count);
            }
        }
        return unreadCount;
    }

    @Override
    public Boolean markFeedRead(List<String> feedIds, long timestamp) {
        ArrayNode feedsArr = JsonNodeFactory.instance.arrayNode();
        feedIds.forEach(f -> {
            TextNode node = JsonNodeFactory.instance.textNode(f);
            feedsArr.add(node);
        });

        ObjectNode json = JsonNodeFactory.instance.objectNode()
            .put("action", "markAsRead")
            .put("asOf", timestamp)
            .put("type", "feeds");

        json.putArray("feedIds")
            .addAll(feedsArr);

        Response response = post("/markers", json, true);
        if (isOk(response.getStatus())){
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public Boolean markArticleRead(List<String> articleIds) {
        return mark(markerJson(articleIds, "markAsRead"));
    }

    @Override
    public Boolean markArticleUnread(List<String> articleIds) {
        return mark(markerJson(articleIds, "keepUnread"));
    }

    @Override
    public Boolean saveArticle(List<String> articleIds) {
        return mark(markerJson(articleIds, "markAsSaved"));
    }

    private static ObjectNode markerJson(List<String> articleIds, String action) {
        ArrayNode articles = JsonNodeFactory.instance.arrayNode();
        articleIds.forEach(a -> {
            TextNode node = JsonNodeFactory.instance.textNode(a);
            articles.add(node);
        });

        ObjectNode json = JsonNodeFactory.instance.objectNode()
            .put("action", action)
            .put("type", "entries");

        json.putArray("entryIds")
            .addAll(articles);
        return json;
    }

    private boolean mark(JsonNode json) {
        Response response = post("/markers", json, true);
        if (isOk(response.getStatus())){
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    private Response post(String url, JsonNode content, boolean refreshIfNeeded) {
        return post(url, Collections.emptyMap(), content, refreshIfNeeded);
    }

    private Response post(String url, Map<String, String> params, JsonNode content, boolean refreshIfNeeded) {
        WebTarget target = client.target(config.get(URL) + url);
        for (Map.Entry<String, String> param : params.entrySet()) {
            target = target.queryParam(param.getKey(), param.getValue());
        }

        Response response = target.request()
            .header("Authorization", "Bearer " + token.getAccessToken())
            .post(Entity.json(content));

        if (isOk(response.getStatus())) {
            return response;
        } else if (refreshIfNeeded && isUnauthorized(response.getStatus())) {
            String refreshedToken = refreshAccessToken();
            token.setAccessToken(refreshedToken);
            token.setRefreshed(true);
            return post(url, params, content,false);
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }
}
