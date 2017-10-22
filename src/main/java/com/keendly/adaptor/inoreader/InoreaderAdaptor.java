package com.keendly.adaptor.inoreader;

import static com.keendly.adaptor.inoreader.InoreaderAdaptor.InoreaderParam.*;
import static com.keendly.utils.ConfigUtils.*;
import static com.keendly.utils.JsonUtils.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.net.UrlEscapers;
import com.keendly.adaptor.GoogleReaderMapper;
import com.keendly.adaptor.GoogleReaderTypeAdaptor;
import com.keendly.adaptor.exception.ApiException;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InoreaderAdaptor extends GoogleReaderTypeAdaptor {

    enum InoreaderParam {
        URL,
        AUTH_URL,
        CLIENT_ID,
        CLIENT_SECRET,
        REDIRECT_URL
    }

    protected Map<InoreaderParam, String> config;

    private JerseyClient client = JerseyClientBuilder.createClient();

    public InoreaderAdaptor(Token token) {
        this(token, defaultConfig());
    }

    public InoreaderAdaptor(Credentials credentials) {
        this(credentials, defaultConfig());
    }

    public InoreaderAdaptor(Token token, Map<InoreaderParam, String> config){
        super(token);
        this.config = config;
    }

    public InoreaderAdaptor(Credentials credentials, Map<InoreaderParam, String> config){
        super();
        this.config = config;
        this.token = login(credentials);
    }

    private static Map<InoreaderParam, String> defaultConfig(){
        Map<InoreaderParam, String> config = new HashMap<>();
        config.put(URL, parameter("INOREADER_URL"));
        config.put(AUTH_URL, parameter("INOREADER_AUTH_URL"));
        config.put(CLIENT_ID, parameter("INOREADER_CLIENT_ID"));
        config.put(CLIENT_SECRET, parameter("INOREADER_CLIENT_SECRET"));
        config.put(REDIRECT_URL, parameter("INOREADER_REDIRECT_URI"));

        return config;
    }

    @Override
    protected Response get(String url) {
        return get(url, Collections.EMPTY_MAP, true);
    }

    protected Response get(String url, Map<String, String> params) {
        return get(url, params, true);
    }

    protected Response post(String url) {
        return post(url, true);
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
            String refreshedToken = refreshAccessToken(token.getRefreshToken());
            token.setAccessToken(refreshedToken);
            token.setRefreshed(true);
            return get(url, params, false);
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    private Response post(String url, boolean refreshIfNeeded) {
        Response response = client.target(config.get(URL) + url)
            .request()
            .header("Authorization", "Bearer " + token.getAccessToken())
            .post(Entity.text(""));

        if (isOk(response.getStatus())) {
            return response;
        } else if (refreshIfNeeded && isUnauthorized(response.getStatus())) {
            String refreshedToken = refreshAccessToken(token.getRefreshToken());
            token.setAccessToken(refreshedToken);
            token.setRefreshed(true);
            return post(url, false);
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    @Override
    public Token login(Credentials credentials) {
        Form form = new Form();
        form.param("code", credentials.getAuthorizationCode());
        form.param("redirect_uri", config.get(REDIRECT_URL));
        form.param("client_id", config.get(CLIENT_ID));
        form.param("client_secret", config.get(CLIENT_SECRET));
        form.param("scope", "write");
        form.param("grant_type", "authorization_code");

        Response response = client.target(config.get(AUTH_URL))
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
        Response response = get("/user-info");
        return GoogleReaderMapper.toUser(asJson(response));
    }

    @Override
    public List<ExternalFeed> getFeeds() {
        Response response = get("/subscription/list");
        return GoogleReaderMapper.toFeeds(asJson(response));
    }

    @Override
    public Map<String, Integer> getUnreadCount(List<String> feedIds) {
        Map<String, Integer> unreadCount = new HashMap<>();
        Response response = get("/unread-count");
        JsonNode node = asJson(response);
        for (JsonNode unread : node.get("unreadcounts")){
            if (feedIds.contains(unread.get("id").asText())){
                unreadCount.put(unread.get("id").asText(), unread.get("count").asInt());
            }
        }
        return unreadCount;
    }

    @Override
    public Boolean markFeedRead(List<String> feedIds, long timestamp) {
        for (String feedId : feedIds){
            Map<String, String> params = new HashMap<>();
            params.put("s", feedId);
            params.put("ts", Long.toString(timestamp * 1000));
            get("/mark-all-as-read", params);
        }
        return Boolean.TRUE;
    }

    @Override
    protected String normalizeFeedId(String feedId){
        return UrlEscapers.urlPathSegmentEscaper().escape(feedId);
    }

    private String refreshAccessToken(String refreshToken){
        Form form = new Form();
        form.param("client_id", config.get(CLIENT_ID));
        form.param("client_secret", config.get(CLIENT_SECRET));
        form.param("grant_type", "refresh_token");
        form.param("refresh_token", refreshToken);

        Response response = client.target(config.get(AUTH_URL))
            .request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            .post(Entity.form(form));

        if (isOk(response.getStatus())) {
            JsonNode node = asJson(response);
            return node.get("access_token").asText();
        } else {
            String body = response.readEntity(String.class);
            if (isInvalidRefreshToken(response.getStatus(), body)){
                throw new ApiException(401, body);
            } else {
                throw new ApiException(response.getStatus(), body);
            }
        }
    }

    protected static boolean isInvalidRefreshToken(int status, String body){
        return status == 400 && body.contains("Invalid refresh token");
    }

    @Override
    public Boolean markArticleRead(List<String> articleIds){
        return editTag(true, "user/-/state/com.google/read", articleIds);
    }

    @Override
    public Boolean markArticleUnread(List<String> articleIds){
        return editTag(false, "user/-/state/com.google/read", articleIds);
    }

    @Override
    public Boolean saveArticle(List<String> articleIds) {
        return editTag(true, "user/-/state/com.google/starred", articleIds);
    }

    private Boolean editTag(boolean add, String tag, List<String> ids){
        String action = add ? "a" : "r";
        String url = "/edit-tag?" + action + "=" + tag;
        for (String id : ids){
            url = url + "&i=" + id;
        }
        Response response = post(url);
        if (isOk(response.getStatus())){
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }
}
