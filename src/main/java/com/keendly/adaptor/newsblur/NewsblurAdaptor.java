package com.keendly.adaptor.newsblur;

import static com.keendly.adaptor.newsblur.NewsblurAdaptor.NewsblurParam.*;
import static com.keendly.utils.ConfigUtils.*;
import static com.keendly.utils.JsonUtils.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.net.UrlEscapers;
import com.keendly.adaptor.Adaptor;
import com.keendly.adaptor.exception.ApiException;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NewsblurAdaptor extends Adaptor {

    private static final Logger LOG = LoggerFactory.getLogger(NewsblurAdaptor.class);

    private static final int TIMEOUT = 5000;

    enum NewsblurParam {
        URL,
        CLIENT_ID,
        CLIENT_SECRET,
        REDIRECT_URL
    }

    protected Map<NewsblurParam, String> config;

    private JerseyClient client = createClient();

    private JerseyClient createClient() {
        JerseyClient client = JerseyClientBuilder.createClient();
        client.property(ClientProperties.READ_TIMEOUT, TIMEOUT);
        return client;
    }

    public NewsblurAdaptor(Token token){
        this(token, defaultConfig());
    }

    public NewsblurAdaptor(Credentials credentials){
        this(credentials, defaultConfig());
    }

    public NewsblurAdaptor(Token token, Map<NewsblurParam, String> config){
        super(token);
        this.config = config;
    }

    public NewsblurAdaptor(Credentials credentials, Map<NewsblurParam, String> config){
        super();
        this.config = config;
        this.token = login(credentials);
    }

    private static Map<NewsblurParam, String> defaultConfig(){
        Map<NewsblurParam, String> config = new HashMap<>();
        config.put(URL, parameter("NEWSBLUR_URL"));
        config.put(CLIENT_ID, parameter("NEWSBLUR_CLIENT_ID"));
        config.put(CLIENT_SECRET, parameter("NEWSBLUR_CLIENT_SECRET"));
        config.put(REDIRECT_URL, parameter("NEWSBLUR_REDIRECT_URI"));
        return config;
    }

    @Override
    public Token login(Credentials credentials) {
        Form form = new Form();
        form.param("code", credentials.getAuthorizationCode());
        form.param("redirect_uri", config.get(REDIRECT_URL));
        form.param("client_id", UrlEscapers.urlFormParameterEscaper().escape(config.get(CLIENT_ID)));
        form.param("client_secret", UrlEscapers.urlFormParameterEscaper().escape(config.get(CLIENT_SECRET)));
        form.param("scope", "write");
        form.param("grant_type", "authorization_code");

        Response response = client.target(config.get(URL) + "/oauth/token")
            .request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            .post(Entity.form(form));

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

    @Override
    public ExternalUser getUser() {
        JsonNode response = get("/social/profile");
        ExternalUser user = new ExternalUser();
        user.setId(response.get("user_id").asText());
        user.setDisplayName(response.get("user_profile").get("username").asText());
        user.setUserName(response.get("user_profile").get("username").asText());

        try {
            JsonNode profile = get("/profile/payment_history");
            if (profile.has("statistics") && profile.get("statistics").has("email")){
                user.setUserName(profile.get("statistics").get("email").asText());
            }
        } catch (Exception e){
            LOG.warn("Couldnt get users email", e);
        }

        return user;
    }

    @Override
    public List<ExternalFeed> getFeeds() {
        JsonNode response = get("/reader/feeds");
        List<ExternalFeed> externalSubscriptions = new ArrayList<>();
        Map<String, List<String>> folders = getFolders(response.get("folders"));
        Iterator<Map.Entry<String, JsonNode>> it = response.get("feeds").fields();
        while (it.hasNext()){
            Map.Entry<String, JsonNode> feed = it.next();
            ExternalFeed externalSubscription = new ExternalFeed();
            String id = feed.getValue().get("id").asText();
            if (folders.containsKey(id)) {
                externalSubscription.setCategories(folders.get(id));
            }
            externalSubscription.setFeedId(feed.getValue().get("id").asText());
            externalSubscription.setTitle(feed.getValue().get("feed_title").asText());
            externalSubscriptions.add(externalSubscription);
        }
        return externalSubscriptions;
    }

    private Map<String, List<String>> getFolders(JsonNode folders) {
        Map<String, List<String>> ret = new HashMap<>();
        for (JsonNode folder : folders) {
            Iterator<String> it = folder.fieldNames();
            while (it.hasNext()){
                String name = it.next();
                Iterator<JsonNode> children = folder.get(name).iterator();
                List<String> ids = new ArrayList<>();
                while (children.hasNext()) {
                    JsonNode child = children.next();
                    String id = child.asText();
                    ids.add(id);
                    if (ret.containsKey(id)){
                        ret.get(id).add(name);
                    } else {
                        List<String> l = new ArrayList<>();
                        l.add(name);
                        ret.put(id, l);
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public Map<String, List<FeedEntry>> getUnread(List<String> feedIds) {
        Map<String, Integer> unreadCounts = getUnreadCount(feedIds);
        List<Map<String, List<FeedEntry>>> results = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : unreadCounts.entrySet()){
            Map<String, List<FeedEntry>> entries = doGetUnread(entry.getKey(), entry.getValue(), 1);
            results.add(entries);
        }

        Map<String, List<FeedEntry>> entries = new HashMap<>();
        for (Map<String, List<FeedEntry>> entry : results) {
            for (Map.Entry<String, List<FeedEntry>> mapEntry : entry.entrySet()) {
                entries.put(mapEntry.getKey(), mapEntry.getValue());
            }
        }
        return entries;
    }

    private Map<String, List<FeedEntry>> doGetUnread(String feedId, int unreadCount, int page) {
        int count = unreadCount > MAX_ARTICLES_PER_FEED ? MAX_ARTICLES_PER_FEED : unreadCount; // TODO inform user
        String url = "/reader/feed/" + UrlEscapers.urlPathSegmentEscaper().escape(feedId) + "?page=" + Integer.toString(page);
        JsonNode jsonResponse = get(url);
        JsonNode items = jsonResponse.get("stories");
        if (items == null){
            return Collections.emptyMap();
        }
        List<FeedEntry> entries = new ArrayList<>();
        for (JsonNode item : items){
            if (item.get("read_status").asInt() == 0){
                FeedEntry entry = mapToFeedEntry(item);
                entries.add(entry);
            }
        }

        Map<String, List<FeedEntry>> ret = new HashMap<>();
        ret.put(feedId, entries);
        if (ret.get(feedId).size() < count){
            Map<String, List<FeedEntry>> nextPage =
                    doGetUnread(feedId, count - ret.get(feedId).size(), page+1);
            ret.get(feedId).addAll(nextPage.get(feedId));
        }

        return ret;
    }

    private static boolean isURL(String s){
        try {
            URI uri = new URI(s);
            if (uri.getScheme() == null || uri.getHost() == null){
                return false;
            }
            return true;
        } catch (Exception e){
            return false;
        }
    }

    @Override
    public Map<String, Integer> getUnreadCount(List<String> feedIds) {
        JsonNode response = get("/reader/refresh_feeds");
        Map<String, Integer> unreadCount = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = response.get("feeds").fields();
        while (it.hasNext()){
            Map.Entry<String, JsonNode> feed = it.next();
            if (feedIds.contains(feed.getKey())){
                unreadCount.put(feed.getKey(), feed.getValue().get("nt").asInt());
            }
        }
        return unreadCount;
    }

    @Override
    public Boolean markFeedRead(List<String> feedIds, long timestamp) {
        for (String feedId : feedIds){
            Form form = new Form();
            form.param("feed_id", feedId);
            form.param("cutoff_timestamp",  String.valueOf(timestamp / 1000)); // to seconds

            Response response = client.target(config.get(URL) + "/reader/mark_feed_as_read")
                .request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .header("Authorization", "Bearer " + token.getAccessToken())
                .post(Entity.form(form));

            if (!isOk(response.getStatus())) {
                throw new ApiException(response.getStatus(), response.readEntity(String.class));
            }
        }
        return Boolean.TRUE;
    }

    @Override
    public Boolean markArticleRead(List<String> articleHashes) {
        return markArticle(articleHashes, "mark_story_hashes_as_read");
    }

    @Override
    public Boolean markArticleUnread(List<String> articleHashes) {
        return markArticle(articleHashes, "mark_story_hash_as_unread");
    }

    @Override
    public Boolean saveArticle(List<String> articleHashes) {
        return markArticle(articleHashes, "mark_story_hash_as_starred");
    }

    private Boolean markArticle(List<String> articleHashes, String path){
        Form form = new Form();
        for (String hash : articleHashes) {
            form.param("story_hash", hash);
        }

        Response response = client.target(config.get(URL) +  "/reader/" + path)
            .request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            .header("Authorization", "Bearer " + token.getAccessToken())
            .post(Entity.form(form));

        if (isOk(response.getStatus())) {
            return Boolean.TRUE;
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    protected JsonNode get(String url) {
        return get(url, Collections.EMPTY_MAP);
    }

    protected JsonNode get(String url, Map<String, List<String>> params){
        WebTarget target = client.target(config.get(URL) + url);
        for (Map.Entry<String, List<String>> param : params.entrySet()) {
            for (String val : param.getValue()){
                target = target.queryParam(param.getKey(), val);
            }
        }

        Response response = target.request()
            .header("Authorization", "Bearer " + token.getAccessToken())
            .get();

        if (isOk(response.getStatus())) {
            JsonNode json = asJson(response);
            if (json.has("authenticated") && !json.get("authenticated").asBoolean()) {
                throw new ApiException(401, "not authenticated");
            } else {
                return json;
            }
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    private static FeedEntry mapToFeedEntry(JsonNode story){
        FeedEntry entry = new FeedEntry();
        String id = asText(story, "id");
        if (isURL(id)){
            entry.setUrl(id);
        } else {
            entry.setUrl(asText(story, "story_permalink"));
        }
        entry.setId(asText(story, "story_hash"));
        entry.setTitle(asText(story, "story_title"));
        entry.setAuthor(asText(story, "story_authors"));
        entry.setPublished(asDate(story, "story_timestamp"));
        entry.setContent(asText(story, "story_content"));
        return entry;
    }
}
