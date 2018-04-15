package com.keendly.adaptor.feedly;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.*;
import static com.keendly.adaptor.AssertHelpers.*;
import static com.keendly.adaptor.feedly.FeedlyAdaptor.FeedlyParam.*;
import static java.util.Arrays.*;
import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.keendly.adaptor.exception.ApiException;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedlyAdaptorTest {

    private static final String TEST_CLIENT_ID = "test";
    private static final String TEST_CLIENT_SECRET = "test2";
    private static final String TEST_REDIRECT_URI = "redirect_uri";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule((wireMockConfig().dynamicPort()));

    @Test
    public void given_ResponseOK_when_login_then_ReturnToken() throws Exception {
        String AUTHORIZATION_CODE = "dummy_auth_code";
        String ACCESS_TOKEN = "dummy_access_token";
        String REFRESH_TOKEN = "dummy_refresh_token";
        int EXPIRES_IN = 60;

        // given
        JSONObject response = new JSONObject();
        response.put("access_token", ACCESS_TOKEN);
        response.put("token_type", "Bearer");
        response.put("expires_in", EXPIRES_IN);
        response.put("refresh_token", REFRESH_TOKEN);
        response.put("scope", "write");

        givenThat(post(urlEqualTo("/auth/token")).willReturn(
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(response.toString())));

        // when
        Credentials credentials = Credentials.builder().authorizationCode(AUTHORIZATION_CODE).build();

        FeedlyAdaptor adaptor = new FeedlyAdaptor(credentials, config());
        Token token = adaptor.getToken();

        // then
        assertEquals(ACCESS_TOKEN, token.getAccessToken());
        assertEquals(REFRESH_TOKEN, token.getRefreshToken());

        verify(postRequestedFor(urlMatching("/auth/token"))
            .withRequestBody(equalToJson("{" +
                    "\"grant_type\" : \"authorization_code\"," +
                    "\"client_id\" : \"test\"," +
                    "\"client_secret\" : \"test2\"," +
                    "\"code\" : \"dummy_auth_code\"," +
                    "\"redirect_uri\" : \"redirect_uri\"" +
                "}"))
            .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    public void given_Error_when_login_then_ThrowException() throws Exception {
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(post(urlEqualTo("/auth/token"))
            .willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            new FeedlyAdaptor(Credentials.builder().build(), config());
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());
    }

    @Test
    public void given_ResponseOK_when_getUser_then_ReturnUser() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String USER_ID = "1001921515";
        String USER_NAME = "BenderIsGreat";
        String USER_EMAIL = "bender@inoreader.com";

        // given
        JSONObject response = new JSONObject();
        response.put("id", USER_ID);
        response.put("fullName", USER_NAME);
        response.put("email", USER_EMAIL);

        givenThat(get(urlEqualTo("/profile"))
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        ExternalUser user = feedlyAdaptor(ACCESS_TOKEN).getUser();

        // then
        assertEquals(USER_ID, user.getId());
        assertEquals(USER_NAME, user.getDisplayName());
        assertEquals(USER_EMAIL, user.getUserName());

        verify(getRequestedFor(urlMatching("/profile"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_Unauthorized_when_getUser_then_RefreshTokenAndRetry() throws Exception {
        String EXPIRED_ACCESS_TOKEN = "my_token";
        String NEW_ACCESS_TOKEN = "my_token1";
        String REFRESH_TOKEN = "refresh_token1";
        String USER_ID = "1001921515";
        String USER_NAME = "BenderIsGreat";
        String USER_EMAIL = "bender@inoreader.com";

        // given
        givenThat(
            get(urlEqualTo("/profile")).inScenario("Get user with refresh").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(403)).willSetStateTo("Forbidden"));

        JSONObject refreshTokenResponse = new JSONObject();
        refreshTokenResponse.put("access_token", NEW_ACCESS_TOKEN);

        givenThat(post(urlEqualTo("/auth/token"))
            .willReturn(aResponse().withStatus(200).withBody(refreshTokenResponse.toString())));

        JSONObject response = new JSONObject();
        response.put("id", USER_ID);
        response.put("fullName", USER_NAME);
        response.put("email", USER_EMAIL);

        givenThat(get(urlEqualTo("/profile")).inScenario("Get user with refresh").whenScenarioStateIs("Forbidden")
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        ExternalUser user = feedlyAdaptor(EXPIRED_ACCESS_TOKEN, REFRESH_TOKEN).getUser();

        // then
        assertEquals(USER_ID, user.getId());
        assertEquals(USER_NAME, user.getDisplayName());
        assertEquals(USER_EMAIL, user.getUserName());

        verify(getRequestedFor(urlMatching("/profile"))
            .withHeader("Authorization", equalTo("Bearer " + EXPIRED_ACCESS_TOKEN)));

        verify(postRequestedFor(urlMatching("/auth/token")).withRequestBody(
            equalToJson("{" +
                    "\"grant_type\":\"refresh_token\"," +
                    "\"client_id\":\"test\"," +
                    "\"client_secret\":\"test2\"," +
                    "\"refresh_token\":\"refresh_token1\"" +
                "}"))
            .withHeader("Content-Type", equalTo("application/json")));

        verify(getRequestedFor(urlMatching("/profile"))
            .withHeader("Authorization", equalTo("Bearer " + NEW_ACCESS_TOKEN)));
    }

    @Test
    public void given_RefreshError_when_getUser_then_ThrowException() throws Exception {
        String EXPIRED_ACCESS_TOKEN = "my_token";
        String REFRESH_TOKEN = "refresh_token";
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(get(urlEqualTo("/profile")).willReturn(aResponse().withStatus(403)));

        givenThat(post(urlEqualTo("/auth/token"))
            .willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            feedlyAdaptor(EXPIRED_ACCESS_TOKEN, REFRESH_TOKEN).getUser();
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());

        verify(getRequestedFor(urlMatching("/profile"))
            .withHeader("Authorization", equalTo("Bearer " + EXPIRED_ACCESS_TOKEN)));
    }

    @Test
    public void given_Error_when_getUser_then_ThrowException() throws Exception {
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(
            get(urlEqualTo("/profile")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            feedlyAdaptor(null).getUser();
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());
    }

    @Test
    public void given_ResponseOK_when_getUnreadCount_then_ReturnUnreadCount() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "test_feed_123";
        int UNREAD_COUNT = 2;

        // given
        JSONObject response = new JSONObject();
        JSONObject feed = new JSONObject();
        feed.put("id", FEED_ID);
        feed.put("count", UNREAD_COUNT);
        response.put("unreadcounts", asList(feed));

        givenThat(get(urlEqualTo("/markers/counts"))
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        Map<String, Integer> unreadCount = feedlyAdaptor(ACCESS_TOKEN).getUnreadCount(asList(FEED_ID));

        // then
        assertEquals(UNREAD_COUNT, unreadCount.get(FEED_ID).intValue());

        verify(getRequestedFor(urlMatching("/markers/counts"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    @Ignore("same refresh token logic as un getUser")
    public void given_Unauthorized_when_getUnreadCount_then_RefreshTokenAndRetry() throws Exception {
        fail();
    }

    @Test
    public void given_Error_when_getUnreadCount_then_ThrowException() throws Exception {
        String FEED_ID = "test_feed_123";
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(
            get(urlEqualTo("/markers/counts")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;

        try {
            feedlyAdaptor(null).getUnreadCount(asList(FEED_ID));
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());
    }

    @Test
    public void given_ResponseOK_when_getUnread_then_ReturnUnreadFeeds() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "feed/https://feeds.feedburner.com/niebezpiecznik/";
        String ESCAPED_FEED_ID = URLEncoder.encode(FEED_ID, "UTF-8");

        String ID1 = "tag:google.com,2005:reader/item/00000000f8b9270e";
        String TITLE1 = "Through the Google lens: Search trends January 16-22";
        String AUTHOR1 = "Emily Wood";
        long PUBLISHED1 = 1422046320;
        String URL1 = "http://feedproxy.google.com/~r/blogspot/MKuf/~3/_Hkdwh7yKMo/blabla.html";
        String CONTENT1 = "test_content";

        String ID2 = "tag:google.com,2005:reader/item/00000000f9ccc3f9";
        String TITLE2 = "Google Maps Engine deprecated";
        String AUTHOR2 = "Timothy Whitehead";
        long PUBLISHED2 = 1422262271;
        String URL2 = "http://feedproxy.google.com/~r/GoogleEarthBlog/~3/HqKBr0Se8K8/google-maps-engine-deprecated.html";
        String CONTENT2 = "test_content2";

        // given
        JSONObject item1 = new FeedlyAdaptorTest.FeedItem().id(ID1).title(TITLE1).author(AUTHOR1).published(PUBLISHED1).url(URL1)
            .content(CONTENT1).unread(true).build();

        JSONObject item2 = new FeedItem().id(ID2).title(TITLE2).author(AUTHOR2).published(PUBLISHED2).url(URL2)
            .content(CONTENT2).unread(true).build();

        JSONObject response = new JSONObject();
        response.put("items", asList(item1, item2));

        givenThat(get(urlMatching("/streams/.*/contents.*"))
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        JSONObject unreadResponse = new JSONObject();
        JSONObject feed1 = new JSONObject();
        feed1.put("id", FEED_ID);
        feed1.put("count", 2);
        unreadResponse.put("unreadcounts", asList(feed1));

        givenThat(get(urlEqualTo("/markers/counts"))
            .willReturn(aResponse().withStatus(200).withBody(unreadResponse.toString())));

        // when
        Map<String, List<FeedEntry>> unread = feedlyAdaptor(ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));
        assertEquals(2, unread.get(FEED_ID).size());
        assertEntryCorrect(unread.get(FEED_ID).get(0), ID1, TITLE1, AUTHOR1, PUBLISHED1, URL1, CONTENT1, true);
        assertEntryCorrect(unread.get(FEED_ID).get(1), ID2, TITLE2, AUTHOR2, PUBLISHED2, URL2, CONTENT2, true);

        verify(getRequestedFor(urlPathEqualTo("/streams/" + ESCAPED_FEED_ID + "/contents"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlEqualTo("/markers/counts"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_MoreResults_when_getUnread_then_FetchNextPage() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "feed/http://feeds.lifehack.org/Lifehack";
        String ESCAPED_FEED_ID = URLEncoder.encode(FEED_ID, "UTF-8");

        String ID1 = "tag:google.com,2005:reader/item/00000000f8b9270e";
        String TITLE1 = "Through the Google lens: Search trends January 16-22";
        String AUTHOR1 = "Emily Wood";
        int PUBLISHED1 = 1422046320;
        String URL1 = "http://feedproxy.google.com/~r/blogspot/MKuf/~3/_Hkdwh7yKMo/blabla.html";
        String CONTENT1 = "test_content";

        String ID2 = "tag:google.com,2005:reader/item/00000000f9ccc3f9";
        String TITLE2 = "Google Maps Engine deprecated";
        String AUTHOR2 = "Timothy Whitehead";
        int PUBLISHED2 = 1422262271;
        String URL2 = "http://feedproxy.google.com/~r/GoogleEarthBlog/~3/HqKBr0Se8K8/google-maps-engine-deprecated.html";
        String CONTENT2 = "test_content2";

        String CONTINUATION = "trMnkg7wWT62";

        // given
        JSONObject item1 = new FeedlyAdaptorTest.FeedItem().id(ID1).title(TITLE1).author(AUTHOR1).published(PUBLISHED1).url(URL1)
            .content(CONTENT1).unread(true).build();

        JSONObject firstResponse = new JSONObject();
        firstResponse.put("items", asList(item1));
        firstResponse.put("continuation", CONTINUATION);

        givenThat(get(urlMatching("/streams/.*/contents.*")).inScenario("Many pages").whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(200).withBody(firstResponse.toString()))
            .willSetStateTo("First page fetched"));

        JSONObject item2 = new FeedItem().id(ID2).title(TITLE2).author(AUTHOR2).published(PUBLISHED2).url(URL2)
            .content(CONTENT2).unread(true).build();

        JSONObject secondResponse = new JSONObject();
        secondResponse.put("items", asList(item2));

        givenThat(
            get(urlMatching("/streams/.*/contents.*")).inScenario("Many pages").whenScenarioStateIs("First page fetched")
                .willReturn(aResponse().withStatus(200).withBody(secondResponse.toString())));

        JSONObject unreadResponse = new JSONObject();
        JSONObject feed1 = new JSONObject();
        feed1.put("id", FEED_ID);
        feed1.put("count", 2);
        unreadResponse.put("unreadcounts", asList(feed1));

        givenThat(get(urlEqualTo("/markers/counts"))
            .willReturn(aResponse().withStatus(200).withBody(unreadResponse.toString())));

        // when
        Map<String, List<FeedEntry>> unread = feedlyAdaptor(ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));
        assertEquals(2, unread.get(FEED_ID).size());
        assertEntryCorrect(unread.get(FEED_ID).get(0), ID1, TITLE1, AUTHOR1, PUBLISHED1, URL1, CONTENT1, true);
        assertEntryCorrect(unread.get(FEED_ID).get(1), ID2, TITLE2, AUTHOR2, PUBLISHED2, URL2, CONTENT2, true);

        verify(getRequestedFor(urlPathEqualTo("/streams/" + ESCAPED_FEED_ID + "/contents"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlPathEqualTo("/streams/" + ESCAPED_FEED_ID + "/contents"))
            .withQueryParam("continuation", equalTo(CONTINUATION))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlEqualTo("/markers/counts"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Accessors(fluent = true)
    @Setter
    private class FeedItem {
        String id, title, author, url, content;
        long published;
        boolean unread;

        JSONObject build() throws Exception {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("title", title);
            item.put("author", author);
            item.put("published", published);
            JSONObject uri = new JSONObject();
            uri.put("href", url);
            uri.put("type", "text/html");
            item.put("alternate", asList(uri));
            JSONObject summary = new JSONObject();
            summary.put("content", content);
            item.put("summary", summary);
            item.put("unread", unread);
            return item;
        }
    }

    @Test
    public void given_Error_when_getUnread_then_ThrowException() throws Exception {
        String FEED_ID = "feed_id";
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        JSONObject unreadResponse = new JSONObject();
        JSONObject feed1 = new JSONObject();
        feed1.put("id", FEED_ID);
        feed1.put("count", 1);
        unreadResponse.put("unreadcounts", asList(feed1));

        givenThat(get(urlEqualTo("/markers/counts"))
            .willReturn(aResponse().withStatus(200).withBody(unreadResponse.toString())));

        givenThat(get(urlMatching("/streams/.*/contents.*"))
            .willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            feedlyAdaptor(null).getUnread(asList(FEED_ID));
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());
    }

    @Test
    public void given_ResponseOK_when_getFeeds_then_ReturnFeeds() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID1 = "feed/http://www.theanimationblog.com/feed/";
        String FEED_TITLE1 = "The Animation Blog.com | Est. 2007";
        String FEED_CATEGORY1 = "awsome category";
        String FEED_ID2 = "feed/http://amanita-design.net/blog/feed/";
        String FEED_TITLE2 = "Amanita Design Blog";
        String FEED_CATEGORY2 = "other category";

        // given
        JSONArray response = new JSONArray();
        JSONObject feed1 = feed(FEED_ID1, FEED_TITLE1, FEED_CATEGORY1);
        JSONObject feed2 = feed(FEED_ID2, FEED_TITLE2, FEED_CATEGORY2);
        response.add(feed1);
        response.add(feed2);

        givenThat(get(urlEqualTo("/subscriptions"))
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        List<ExternalFeed> feeds = feedlyAdaptor(ACCESS_TOKEN).getFeeds();

        // then
        assertEquals(2, feeds.size());
        assertFeedCorrect(feeds.get(0), FEED_TITLE1, FEED_ID1, FEED_CATEGORY1);
        assertFeedCorrect(feeds.get(1), FEED_TITLE2, FEED_ID2, FEED_CATEGORY2);

        verify(getRequestedFor(urlPathEqualTo("/subscriptions"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    private JSONObject feed(String id, String title, String category){
        JSONObject item = new JSONObject();
        JSONArray arr = new JSONArray();
        JSONObject cat = new JSONObject();
        cat.put("label", category);
        arr.add(cat);
        item.put("title", title);
        item.put("id", id);
        item.put("categories", arr);
        return item;
    }

    @Test
    @Ignore("same refresh token logic as un getUser")
    public void given_Unauthorized_when_getFeeds_then_RefreshTokenAndRetry() throws Exception {
        fail();
    }

    @Test
    public void given_Error_when_getFeeds_then_ThrowException() throws Exception {
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(get(urlEqualTo("/subscriptions"))
            .willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;

        try {
            feedlyAdaptor(null).getFeeds().get(1000);
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());
    }

    @Test
    public void given_ResponseOK_when_markAsRead_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID1 = "feed/http://www.sprengsatz.de/?feed=rss2";
        String FEED_ID2 = "feed/http://warszawskibiegacz.pl/?feed=rss2";
        long timestamp = System.currentTimeMillis();

        // given
        givenThat(post(urlEqualTo("/markers")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = feedlyAdaptor(ACCESS_TOKEN).markFeedRead(asList(FEED_ID1, FEED_ID2), timestamp);

        // then
        assertTrue(success);

        verify(postRequestedFor(urlEqualTo("/markers"))
            .withRequestBody(equalToJson("{" +
                    "\"action\" : \"markAsRead\"," +
                    "\"feedIds\" : [" +
                        "\"feed/http://www.sprengsatz.de/?feed=rss2\"," +
                        "\"feed/http://warszawskibiegacz.pl/?feed=rss2\"" +
                    "]," +
                    "\"asOf\" : " + timestamp + "," +
                    "\"type\" : \"feeds\"" +
                "}"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_Error_when_markAsRead_then_ThrowException() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "test_feed_123";
        int ERROR_STATUS_CODE = 500;

        long timestamp = System.currentTimeMillis();

        // given
        givenThat(post(urlEqualTo("/markers")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE)));

        // when
        Exception thrown = null;
        try {
            feedlyAdaptor(ACCESS_TOKEN).markFeedRead(asList(FEED_ID), timestamp);
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
    }

    @Test
    public void given_ResponseOK_when_markArticleRead_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1628e050286:686b9b:76251cdd";
        String ARTICLE_ID2 = "IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1625360ba71:3036f:a077e55f";

        // given
        givenThat(post(urlEqualTo("/markers")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = feedlyAdaptor(ACCESS_TOKEN).markArticleRead(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlEqualTo("/markers"))
            .withRequestBody(equalToJson("{" +
                    "\"action\" : \"markAsRead\"," +
                    "\"entryIds\" : [" +
                        "\"IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1628e050286:686b9b:76251cdd\"," +
                        "\"IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1625360ba71:3036f:a077e55f\"" +
                    "]," +
                    "\"type\" : \"entries\"" +
                "}"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_markArticleUnread_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1628e050286:686b9b:76251cdd";
        String ARTICLE_ID2 = "IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1625360ba71:3036f:a077e55f";

        // given
        givenThat(post(urlEqualTo("/markers")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = feedlyAdaptor(ACCESS_TOKEN).markArticleUnread(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlEqualTo("/markers"))
            .withRequestBody(equalToJson("{" +
                    "\"action\" : \"keepUnread\"," +
                    "\"entryIds\" : [" +
                        "\"IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1628e050286:686b9b:76251cdd\"," +
                        "\"IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1625360ba71:3036f:a077e55f\"" +
                    "]," +
                    "\"type\" : \"entries\"" +
                "}"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_saveArticle_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1628e050286:686b9b:76251cdd";
        String ARTICLE_ID2 = "IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1625360ba71:3036f:a077e55f";

        // given
        givenThat(post(urlEqualTo("/markers")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = feedlyAdaptor(ACCESS_TOKEN).saveArticle(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlEqualTo("/markers"))
            .withRequestBody(equalToJson("{" +
                    "\"action\" : \"markAsSaved\"," +
                    "\"entryIds\" : [" +
                        "\"IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1628e050286:686b9b:76251cdd\"," +
                        "\"IRVzqLsuoUK0H2nUbo0iyjvOdXWsi3qoOtq3AFqOWsw=_1625360ba71:3036f:a077e55f\"" +
                    "]," +
                    "\"type\" : \"entries\"" +
                "}"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    private FeedlyAdaptor feedlyAdaptor(String accessToken) {
        return feedlyAdaptor(accessToken, null);
    }

    private FeedlyAdaptor feedlyAdaptor(String accessToken, String refreshToken) {
        Token token = Token.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
        return new FeedlyAdaptor(token, config());
    }

    @Test
    public void test() {
        Credentials credentials = Credentials.builder()
            .authorizationCode("A0zS0NbIov4-dWV84WKUtQneOqBMSyy8ZAk_7LWMm09Zg-7V2L7H68Gxf4Jnb9RT1Wl7itT-Uv_qVE1TEtXrE-lISIeakv2zcu8Wd9dybJEEY7YyXGrhCe14iS8D_xFeZxwWj7hxUtfqcByKz5UPOnohVOPxVO6_wJ7fIcdqCYkyS3nc56VHKW_W7g")
            .build();

        Token t = Token.builder()
            .accessToken("A_kqJZOUoctdMjBv7WKX7MT2ux9FFfdzr6dSOkDuegLWee4alhlcCq6hYO4Dvo5_QXoYWgIchJa7vKuZkLbqFVQgkehLopNEMgtlw2FKzm1yAGU5lVPgmTNl7SzmxpjzYu7Z3JvBOzfOUpt8VhwKxpuIkpw7cwmLYZk5Oh6dsSAi692hqAuCig3ly5WRPza_A9EAvjTJMlY_vw1bYtigP7CSwrQCsi-UpxK8oLE6YYEs16u6ZQ:keendly")
            .refreshToken("A5RRaityN4IUDp832kWScP4Fjd9XQdyR0yEzcmkmMOPkiJIl6QVnr20JVSJoHFIYf53qj-x7O-agEvJki8kuiwsM92R_kNcrK7PJQ3LZK0GtiWjSnjpylcxYf8yefEKcN_4Ivi2KtZjGq0mMyLT1nqBNnGBLmWaNZWNA0-q8X7vp_5v-YfCIpU8CqNyl0RJWaT5F9QaOd1JkBC7fI9x5-VV3JDSg9V9mPfBC0qYtvfjqGe7m8ODpiiXU:keendly")
            .build();

        FeedlyAdaptor adaptor = new FeedlyAdaptor(t, prodConfig());

        List<ExternalFeed> feeds = adaptor.getFeeds();
//        Map<String, Integer> counts = adaptor.getUnreadCount(Arrays.asList("feed/http://antyweb.pl/feed/", "feed/http://techblog.netflix.com/feeds/posts/default", "feed/http://runtheworld.pl/feed/"));
        Map<String, List<FeedEntry>> unread = adaptor.getUnread(Arrays.asList("feed/http://warszawskibiegacz.pl/?feed=rss2"));


//        adaptor.markFeedRead(Arrays.asList("feed/http://techblog.netflix.com/feeds/posts/default", "feed/http://antyweb.pl/feed/"), System.currentTimeMillis());
//        String refreshed = adaptor.refreshAccessToken();

        String a = "a";
    }

    private static Map<FeedlyAdaptor.FeedlyParam, String> prodConfig(){
        Map<FeedlyAdaptor.FeedlyParam, String> config = new HashMap<>();
        config.put(URL, "https://cloud.feedly.com/v3");
        config.put(CLIENT_ID, "keendly");
        config.put(CLIENT_SECRET, "FE01SFTW2VHNNMQ6AF099KMOJ0A5");
        config.put(REDIRECT_URL, "https://app.keendly.com/feedlyCallback");

        return config;
    }

    private Map<FeedlyAdaptor.FeedlyParam, String> config() {
        Map<FeedlyAdaptor.FeedlyParam, String> config = new HashMap<>();
        config.put(URL, "http://localhost:" + wireMockRule.port());
        config.put(CLIENT_ID, TEST_CLIENT_ID);
        config.put(CLIENT_SECRET, TEST_CLIENT_SECRET);
        config.put(REDIRECT_URL, TEST_REDIRECT_URI);
        return config;
    }
}
