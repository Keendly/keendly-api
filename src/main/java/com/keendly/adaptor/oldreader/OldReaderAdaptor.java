package com.keendly.adaptor.oldreader;

import static com.keendly.adaptor.oldreader.OldReaderAdaptor.OldReaderParam.*;
import static com.keendly.utils.ConfigUtils.*;
import static com.keendly.utils.JsonUtils.*;

import com.fasterxml.jackson.databind.JsonNode;
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

public class OldReaderAdaptor extends GoogleReaderTypeAdaptor {

    private static final String APP_NAME = "Keendly";

    enum OldReaderParam {
        URL,
        AUTH_URL
    }

    protected Map<OldReaderParam, String> config;

    private JerseyClient client = JerseyClientBuilder.createClient();

    public OldReaderAdaptor(Token token){
        this(token, defaultConfig());
    }

    public OldReaderAdaptor(Credentials credentials){
        this(credentials, defaultConfig());
    }

    public OldReaderAdaptor(Token token, Map<OldReaderParam, String> config){
        super(token);
        this.config = config;
    }

    public OldReaderAdaptor(Credentials credentials, Map<OldReaderParam, String> config){
        super();
        this.config = config;
        this.token = login(credentials);
    }

    public static Map<OldReaderParam, String> defaultConfig(){
        Map<OldReaderParam, String> config = new HashMap<>();
        config.put(URL, parameter("OLDREADER_URL"));
        config.put(AUTH_URL, parameter("OLDREADER_AUTH_URL"));
        return config;
    }

    @Override
    public Token login(Credentials credentials) {
        Form form = new Form();
        form.param("client", APP_NAME);
        form.param("accountType", "HOSTED_OR_GOOGLE");
        form.param("service", "reader");
        form.param("Email", credentials.getUsername());
        form.param("Passwd", credentials.getPassword());

        Response response = client.target(config.get(AUTH_URL))
            .request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            .post(Entity.form(form));

        if (isOk(response.getStatus())) {
            String responseContent = response.readEntity(String.class);
            String token = extractToken(responseContent);
            if (token == null) {
                throw new ApiException(503, responseContent);
            }
            return Token.builder()
                .accessToken(token)
                .build();
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    private String extractToken(String response){
        String[] params = response.split("\n");
        for (String param : params){
            String[] p = param.split("=");
            if (p.length == 2 && p[0].equals("Auth")){
                return p[1];
            }
        }
        return null;
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
            Form form = new Form();
            form.param("s", feedId);
            form.param("ts", String.valueOf(timestamp * 1000000));

            client.target(config.get(URL) + "/mark-all-as-read")
                .request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .header("Authorization", "GoogleLogin auth=" + token.getAccessToken())
                .post(Entity.form(form));
        }

        return Boolean.TRUE;
    }

    @Override
    protected Response get(String url) {
        return get(url, Collections.EMPTY_MAP);
    }

    private Response get(String url, Map<String, String> params){
        WebTarget target = client.target(config.get(URL) + normalizeURL(url));
        for (Map.Entry<String, String> param : params.entrySet()) {
            target = target.queryParam(param.getKey(), param.getValue());
        }

        Response response = target.request()
            .header("Authorization", "GoogleLogin auth=" + token.getAccessToken())
            .get();

        if (isOk(response.getStatus())) {
            return response;
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    protected Response post(String url, Form form) {
        Response response = client.target(config.get(URL) + normalizeURL(url))
            .request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            .header("Authorization", "GoogleLogin auth=" + token.getAccessToken())
            .post(Entity.form(form));

        if (isOk(response.getStatus())) {
            return response;
        } else {
            throw new ApiException(response.getStatus(), response.readEntity(String.class));
        }
    }

    private static String normalizeURL(String url){
        if (url.contains("?")){
            return url + "&output=json";
        } else {
            return url + "?output=json";
        }
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
        Form form = new Form();
        form.param(add ? "a" : "r", tag);
        for (String id : ids){
            form.param("i", id);
        }

        Response response =  post("/edit-tag", form);
        if (isOk(response.getStatus())){
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }
}
