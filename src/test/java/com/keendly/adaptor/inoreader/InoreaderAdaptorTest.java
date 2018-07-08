package com.keendly.adaptor.inoreader;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.keendly.adaptor.AssertHelpers.*;
import static java.util.Arrays.*;
import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.common.net.UrlEscapers;
import com.keendly.adaptor.exception.ApiException;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;
import com.keendly.dao.DeliveryDao;
import com.keendly.model.Delivery;
import com.keendly.model.DeliveryItem;
import com.keendly.utils.DbUtils;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.http.HttpStatus;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InoreaderAdaptorTest {

    private static final int PORT = 8089;
    private static final String CLIENT_ID = "test";
    private static final String CLIENT_SECRET = "test2";
    private static final String REDIRECT_URI = "redirect_uri";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

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

        givenThat(post(urlEqualTo("/auth")).willReturn(
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(response.toString())));

        // when
        Credentials credentials = Credentials.builder().authorizationCode(AUTHORIZATION_CODE).build();

        InoreaderAdaptor adaptor = new InoreaderAdaptor(credentials, config());
        Token token = adaptor.getToken();

        // then
        assertEquals(ACCESS_TOKEN, token.getAccessToken());
        assertEquals(REFRESH_TOKEN, token.getRefreshToken());

        verify(postRequestedFor(urlMatching("/auth")).withRequestBody(
            thatContainsParams(param("code", AUTHORIZATION_CODE), param("redirect_uri", REDIRECT_URI),
                param("client_id", CLIENT_ID), param("client_secret", CLIENT_SECRET), param("scope", "write"),
                param("grant_type", "authorization_code")))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));
    }

    @Test
    public void given_Error_when_login_then_ThrowException() throws Exception {
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            new InoreaderAdaptor(Credentials.builder().build(), config());
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
        response.put("userId", USER_ID);
        response.put("userName", USER_NAME);
        response.put("userProfileId", "1001921515");
        response.put("userEmail", USER_EMAIL);
        response.put("isBloggerUser", true);
        response.put("signupTimeSec", 1163850013);
        response.put("isMultiLoginEnabled", false);

        givenThat(get(urlEqualTo("/user-info")).willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        ExternalUser user = inoreaderAdaptor(ACCESS_TOKEN).getUser();

        // then
        assertEquals(USER_ID, user.getId());
        assertEquals(USER_NAME, user.getDisplayName());
        assertEquals(USER_EMAIL, user.getUserName());

        verify(
            getRequestedFor(urlMatching("/user-info")).withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_Unauthorized_when_getUser_then_RefreshTokenAndRetry() throws Exception {
        String EXPIRED_ACCESS_TOKEN = "my_token";
        String NEW_ACCESS_TOKEN = "my_token1";
        String REFRESH_TOKEN = "refresh_token";
        String USER_ID = "1001921515";
        String USER_NAME = "BenderIsGreat";
        String USER_EMAIL = "bender@inoreader.com";

        // given
        givenThat(
            get(urlEqualTo("/user-info")).inScenario("Get user with refresh").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(403)).willSetStateTo("Forbidden"));

        JSONObject refreshTokenResponse = new JSONObject();
        refreshTokenResponse.put("access_token", NEW_ACCESS_TOKEN);

        givenThat(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200).withBody(refreshTokenResponse.toString())));

        JSONObject response = new JSONObject();
        response.put("userId", USER_ID);
        response.put("userName", USER_NAME);
        response.put("userEmail", USER_EMAIL);

        givenThat(get(urlEqualTo("/user-info")).inScenario("Get user with refresh").whenScenarioStateIs("Forbidden")
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        ExternalUser user = inoreaderAdaptor(EXPIRED_ACCESS_TOKEN, REFRESH_TOKEN).getUser();

        // then
        assertEquals(USER_ID, user.getId());
        assertEquals(USER_NAME, user.getDisplayName());
        assertEquals(USER_EMAIL, user.getUserName());

        verify(getRequestedFor(urlMatching("/user-info"))
            .withHeader("Authorization", equalTo("Bearer " + EXPIRED_ACCESS_TOKEN)));

        verify(postRequestedFor(urlMatching("/auth")).withRequestBody(
            thatContainsParams(param("client_id", CLIENT_ID), param("client_secret", CLIENT_SECRET),
                param("grant_type", "refresh_token"), param("refresh_token", REFRESH_TOKEN)))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));

        verify(getRequestedFor(urlMatching("/user-info"))
            .withHeader("Authorization", equalTo("Bearer " + NEW_ACCESS_TOKEN)));
    }

    @Test
    public void given_RefreshError_when_getUser_then_ThrowException() throws Exception {
        String EXPIRED_ACCESS_TOKEN = "my_token";
        String REFRESH_TOKEN = "refresh_token";
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(get(urlEqualTo("/user-info")).willReturn(aResponse().withStatus(403)));

        givenThat(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            inoreaderAdaptor(EXPIRED_ACCESS_TOKEN, REFRESH_TOKEN).getUser();
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());

        verify(getRequestedFor(urlMatching("/user-info"))
            .withHeader("Authorization", equalTo("Bearer " + EXPIRED_ACCESS_TOKEN)));

        verify(postRequestedFor(urlMatching("/auth")).withRequestBody(
            thatContainsParams(param("client_id", CLIENT_ID), param("client_secret", CLIENT_SECRET),
                param("grant_type", "refresh_token"), param("refresh_token", REFRESH_TOKEN)))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));

    }

    @Test
    public void given_Error_when_getUser_then_ThrowException() throws Exception {
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(
            get(urlEqualTo("/user-info")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            inoreaderAdaptor(null).getUser();
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

        givenThat(
            get(urlEqualTo("/unread-count")).willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        Map<String, Integer> unreadCount = inoreaderAdaptor(ACCESS_TOKEN).getUnreadCount(asList(FEED_ID));

        // then
        assertEquals(UNREAD_COUNT, unreadCount.get(FEED_ID).intValue());

        verify(getRequestedFor(urlMatching("/unread-count"))
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
            get(urlEqualTo("/unread-count")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;

        try {
            inoreaderAdaptor(null).getUnreadCount(asList(FEED_ID));
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
        String ESCAPED_FEED_ID = UrlEscapers.urlPathSegmentEscaper().escape(FEED_ID);

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
        JSONObject item1 = new FeedItem().id(ID1).title(TITLE1).author(AUTHOR1).published(PUBLISHED1).url(URL1)
            .content(CONTENT1).build();

        JSONObject item2 = new FeedItem().id(ID2).title(TITLE2).author(AUTHOR2).published(PUBLISHED2).url(URL2)
            .content(CONTENT2).build();

        JSONObject response = new JSONObject();
        response.put("items", asList(item1, item2));

        givenThat(get(urlMatching("/stream/contents/.*"))
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        JSONObject unreadResponse = new JSONObject();
        JSONObject feed1 = new JSONObject();
        feed1.put("id", FEED_ID);
        feed1.put("count", 2);
        unreadResponse.put("unreadcounts", asList(feed1));

        givenThat(get(urlEqualTo("/unread-count"))
            .willReturn(aResponse().withStatus(200).withBody(unreadResponse.toString())));

        // when
        Map<String, List<FeedEntry>> unread = inoreaderAdaptor(ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));
        assertEquals(2, unread.get(FEED_ID).size());
        assertEntryCorrect(unread.get(FEED_ID).get(0), ID1, TITLE1, AUTHOR1, PUBLISHED1, URL1, CONTENT1);
        assertEntryCorrect(unread.get(FEED_ID).get(1), ID2, TITLE2, AUTHOR2, PUBLISHED2, URL2, CONTENT2);

        verify(getRequestedFor(urlPathEqualTo("/stream/contents/" + ESCAPED_FEED_ID))
            .withQueryParam("xt", equalTo("user/-/state/com.google/read"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlEqualTo("/unread-count"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_MoreResults_when_getUnread_then_FetchNextPage() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "feed/http://feeds.lifehack.org/Lifehack";
        String ESCAPED_FEED_ID = UrlEscapers.urlPathSegmentEscaper().escape(FEED_ID);

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
        JSONObject item1 = new FeedItem().id(ID1).title(TITLE1).author(AUTHOR1).published(PUBLISHED1).url(URL1)
            .content(CONTENT1).build();

        JSONObject firstResponse = new JSONObject();
        firstResponse.put("items", asList(item1));
        firstResponse.put("continuation", CONTINUATION);

        givenThat(get(urlMatching("/stream/contents/.*")).inScenario("Many pages").whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(200).withBody(firstResponse.toString()))
            .willSetStateTo("First page fetched"));

        JSONObject item2 = new FeedItem().id(ID2).title(TITLE2).author(AUTHOR2).published(PUBLISHED2).url(URL2)
            .content(CONTENT2).build();

        JSONObject secondResponse = new JSONObject();
        secondResponse.put("items", asList(item2));

        givenThat(
            get(urlMatching("/stream/contents/.*")).inScenario("Many pages").whenScenarioStateIs("First page fetched")
                .willReturn(aResponse().withStatus(200).withBody(secondResponse.toString())));

        JSONObject unreadResponse = new JSONObject();
        JSONObject feed1 = new JSONObject();
        feed1.put("id", FEED_ID);
        feed1.put("count", 2);
        unreadResponse.put("unreadcounts", asList(feed1));

        givenThat(get(urlEqualTo("/unread-count"))
            .willReturn(aResponse().withStatus(200).withBody(unreadResponse.toString())));

        // when
        Map<String, List<FeedEntry>> unread = inoreaderAdaptor(ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));
        assertEquals(2, unread.get(FEED_ID).size());
        assertEntryCorrect(unread.get(FEED_ID).get(0), ID1, TITLE1, AUTHOR1, PUBLISHED1, URL1, CONTENT1);
        assertEntryCorrect(unread.get(FEED_ID).get(1), ID2, TITLE2, AUTHOR2, PUBLISHED2, URL2, CONTENT2);

        verify(getRequestedFor(urlPathEqualTo("/stream/contents/" + ESCAPED_FEED_ID))
            .withQueryParam("xt", equalTo("user/-/state/com.google/read"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlPathEqualTo("/stream/contents/" + ESCAPED_FEED_ID))
            .withQueryParam("c", equalTo(CONTINUATION)).withQueryParam("xt", equalTo("user/-/state/com.google/read"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlEqualTo("/unread-count"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_Unauthorized_when_getUnread_then_RefreshTokenAndRetry() throws Exception {
        String EXPIRED_ACCESS_TOKEN = "my_token";
        String NEW_ACCESS_TOKEN = "my_token11";

        String FEED_ID = "feed_id";

        String TITLE1 = "Through the Google lens: Search trends January 16-22";
        String AUTHOR1 = "Emily Wood";
        int PUBLISHED1 = 1422046320;
        String URL1 = "http://feedproxy.google.com/~r/blogspot/MKuf/~3/_Hkdwh7yKMo/blabla.html";
        String CONTENT1 = "test_content";

        // given
        givenThat(
            get(urlEqualTo("/unread-count")).inScenario("Get unread with refresh").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(403)).willSetStateTo("Forbidden"));

        JSONObject refreshTokenResponse = new JSONObject();
        refreshTokenResponse.put("access_token", NEW_ACCESS_TOKEN);

        givenThat(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200).withBody(refreshTokenResponse.toString())));

        JSONObject unreadResponse = new JSONObject();
        JSONObject feed1 = new JSONObject();
        feed1.put("id", FEED_ID);
        feed1.put("count", 2);
        unreadResponse.put("unreadcounts", asList(feed1));

        givenThat(
            get(urlEqualTo("/unread-count")).inScenario("Get unread with refresh").whenScenarioStateIs("Forbidden")
                .willReturn(aResponse().withStatus(200).withBody(unreadResponse.toString())));

        JSONObject item1 = new FeedItem().title(TITLE1).author(AUTHOR1).published(PUBLISHED1).url(URL1)
            .content(CONTENT1).build();
        JSONObject response = new JSONObject();
        response.put("items", asList(item1));

        givenThat(get(urlMatching("/stream/contents/.*"))
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        Map<String, List<FeedEntry>> unread = inoreaderAdaptor(EXPIRED_ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));

        verify(getRequestedFor(urlPathEqualTo("/unread-count"))
            .withHeader("Authorization", equalTo("Bearer " + EXPIRED_ACCESS_TOKEN)));

        verify(getRequestedFor(urlPathEqualTo("/unread-count"))
            .withHeader("Authorization", equalTo("Bearer " + NEW_ACCESS_TOKEN)));

    }

    @Accessors(fluent = true)
    @Setter
    private class FeedItem {
        String id, title, author, url, content;
        long published;

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

        givenThat(get(urlEqualTo("/unread-count"))
            .willReturn(aResponse().withStatus(200).withBody(unreadResponse.toString())));

        givenThat(get(urlMatching("/stream/contents/.*"))
            .willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            inoreaderAdaptor(null).getUnread(asList(FEED_ID));
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
        String FEED_ID2 = "feed/http://fulltext.foxhole.work/makefulltextfeed.php?url=sports.espn.go.com%2Fespn%2Frss%2Fnews&max=20&links=preserve";
        long timestamp = System.currentTimeMillis();

        // given
        givenThat(get(urlMatching("/mark-all-as-read.*")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = inoreaderAdaptor(ACCESS_TOKEN).markFeedRead(asList(FEED_ID1, FEED_ID2), timestamp);

        // then
        assertTrue(success);

        verify(getRequestedFor(urlPathEqualTo("/mark-all-as-read"))
            .withQueryParam("s", equalTo(FEED_ID1))
            .withQueryParam("ts", equalTo(Long.toString(timestamp * 1000)))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlPathEqualTo("/mark-all-as-read"))
            .withQueryParam("s", equalTo(FEED_ID2))
            .withQueryParam("ts", equalTo(Long.toString(timestamp * 1000)))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_Unauthorized_when_markAsRead_then_RefreshTokenAndRetry() throws Exception {
        String EXPIRED_ACCESS_TOKEN = "my_token";
        String NEW_ACCESS_TOKEN = "my_token1";
        String REFRESH_TOKEN = "refresh_token";
        String FEED_ID1 = "feed/http://www.sprengsatz.de/?feed=rss2";
        String FEED_ID2 = "feed/http://warszawskibiegacz.pl/?feed=rss2";
        long timestamp = System.currentTimeMillis();

        // given
        givenThat(get(urlMatching("/mark-all-as-read.*")).inScenario("Mark as read with refresh")
            .whenScenarioStateIs(Scenario.STARTED).willReturn(aResponse().withStatus(403)).willSetStateTo("Forbidden"));

        JSONObject refreshTokenResponse = new JSONObject();
        refreshTokenResponse.put("access_token", NEW_ACCESS_TOKEN);

        givenThat(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200).withBody(refreshTokenResponse.toString())));

        givenThat(get(urlMatching("/mark-all-as-read.*")).inScenario("Mark as read with refresh")
            .whenScenarioStateIs("Forbidden").willReturn(aResponse().withStatus(200)));

        // when
        boolean success = inoreaderAdaptor(EXPIRED_ACCESS_TOKEN, REFRESH_TOKEN)
            .markFeedRead(asList(FEED_ID1, FEED_ID2), timestamp);

        // then
        assertTrue(success);

        verify(getRequestedFor(urlPathEqualTo("/mark-all-as-read")).withQueryParam("s", equalTo(FEED_ID1))
            .withQueryParam("ts", equalTo(Long.toString(timestamp * 1000)))
            .withHeader("Authorization", equalTo("Bearer " + EXPIRED_ACCESS_TOKEN)));

        verify(postRequestedFor(urlMatching("/auth")).withRequestBody(
            thatContainsParams(param("client_id", CLIENT_ID), param("client_secret", CLIENT_SECRET),
                param("grant_type", "refresh_token"), param("refresh_token", REFRESH_TOKEN)))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));

        verify(getRequestedFor(urlPathEqualTo("/mark-all-as-read")).withQueryParam("s", equalTo(FEED_ID1))
            .withQueryParam("ts", equalTo(Long.toString(timestamp * 1000)))
            .withHeader("Authorization", equalTo("Bearer " + NEW_ACCESS_TOKEN)));
    }

    @Test
    public void given_Error_when_markAsRead_then_ThrowException() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "test_feed_123";
        int ERROR_STATUS_CODE = 500;

        long timestamp = System.currentTimeMillis();

        // given
        givenThat(get(urlMatching("/mark-all-as-read.*")).willReturn(aResponse().withStatus(ERROR_STATUS_CODE)));

        // when
        Exception thrown = null;
        try {
            inoreaderAdaptor(ACCESS_TOKEN).markFeedRead(asList(FEED_ID), timestamp);
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());

        verify(getRequestedFor(urlPathEqualTo("/mark-all-as-read")).withQueryParam("s", equalTo(FEED_ID))
            .withQueryParam("ts", equalTo(Long.toString(timestamp * 1000)))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
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
        JSONObject response = new JSONObject();
        JSONObject feed1 = feed(FEED_ID1, FEED_TITLE1, FEED_CATEGORY1);
        JSONObject feed2 = feed(FEED_ID2, FEED_TITLE2, FEED_CATEGORY2);
        response.put("subscriptions", asList(feed1, feed2));

        givenThat(get(urlEqualTo("/subscription/list"))
            .willReturn(aResponse().withStatus(200).withBody(response.toString())));

        // when
        List<ExternalFeed> feeds = inoreaderAdaptor(ACCESS_TOKEN).getFeeds();

        // then
        assertEquals(2, feeds.size());
        assertFeedCorrect(feeds.get(0), FEED_TITLE1, FEED_ID1, FEED_CATEGORY1);
        assertFeedCorrect(feeds.get(1), FEED_TITLE2, FEED_ID2, FEED_CATEGORY2);

        verify(getRequestedFor(urlPathEqualTo("/subscription/list"))
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
    public void given_InvalidRefreshToken_when_getFeeds_then_ThrowUnauthorizedException() throws Exception {
        // given
        givenThat(get(urlEqualTo("/subscription/list")).willReturn(aResponse().withStatus(403)));

        JSONObject refreshTokenResponse = new JSONObject();
        refreshTokenResponse.put("error", "invalid_grant");
        refreshTokenResponse.put("error_description", "Invalid refresh token");

        givenThat(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(400).withBody(refreshTokenResponse.toString())));

        // when
        Exception thrown = null;

        try {
            inoreaderAdaptor(null).getFeeds();
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(HttpStatus.SC_UNAUTHORIZED, ((ApiException) thrown).getStatus());
    }

    @Test
    public void given_Error_when_getFeeds_then_ThrowException() throws Exception {
        int ERROR_STATUS_CODE = 500;
        String RESPONSE = "error";

        // given
        givenThat(get(urlEqualTo("/subscription/list"))
            .willReturn(aResponse().withStatus(ERROR_STATUS_CODE).withBody(RESPONSE)));

        // when
        Exception thrown = null;

        try {
            inoreaderAdaptor(null).getFeeds().get(1000);
        } catch (Exception e) {
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(ERROR_STATUS_CODE, ((ApiException) thrown).getStatus());
        assertEquals(RESPONSE, ((ApiException) thrown).getResponse());
    }

    @Test
    public void given_ResponseOK_when_markArticleRead_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "tag:google.com,2005:reader/item/00000002440bbbfd";
        String ARTICLE_ID2 = "tag:google.com,2005:reader/item/00000002437d0c74";

        // given
        givenThat(post(urlMatching("/edit-tag.*")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = inoreaderAdaptor(ACCESS_TOKEN).markArticleRead(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(
            postRequestedFor(urlPathEqualTo("/edit-tag")).withQueryParam("a", equalTo("user/-/state/com.google/read"))
                .withQueryParam("i", equalTo(ARTICLE_ID1)).withQueryParam("i", equalTo(ARTICLE_ID2))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_markArticleUnread_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "tag:google.com,2005:reader/item/00000002440bbbfd";
        String ARTICLE_ID2 = "tag:google.com,2005:reader/item/00000002437d0c74";

        // given
        givenThat(post(urlMatching("/edit-tag.*")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = inoreaderAdaptor(ACCESS_TOKEN).markArticleUnread(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(
            postRequestedFor(urlPathEqualTo("/edit-tag")).withQueryParam("r", equalTo("user/-/state/com.google/read"))
                .withQueryParam("i", equalTo(ARTICLE_ID1)).withQueryParam("i", equalTo(ARTICLE_ID2))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_saveArticle_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "tag:google.com,2005:reader/item/00000002440bbbfd";
        String ARTICLE_ID2 = "tag:google.com,2005:reader/item/00000002437d0c74";

        // given
        givenThat(post(urlMatching("/edit-tag.*")).willReturn(aResponse().withStatus(200)));

        // when
        boolean success = inoreaderAdaptor(ACCESS_TOKEN).saveArticle(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/edit-tag"))
            .withQueryParam("a", equalTo("user/-/state/com.google/starred")).withQueryParam("i", equalTo(ARTICLE_ID1))
            .withQueryParam("i", equalTo(ARTICLE_ID2)).withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    private static Map<InoreaderAdaptor.InoreaderParam, String> config() {
        Map<InoreaderAdaptor.InoreaderParam, String> config = new HashMap<>();
        config.put(InoreaderAdaptor.InoreaderParam.URL, "http://localhost:" + PORT);
        config.put(InoreaderAdaptor.InoreaderParam.AUTH_URL, "http://localhost:" + PORT + "/auth");
        config.put(InoreaderAdaptor.InoreaderParam.CLIENT_ID, CLIENT_ID);
        config.put(InoreaderAdaptor.InoreaderParam.CLIENT_SECRET, CLIENT_SECRET);
        config.put(InoreaderAdaptor.InoreaderParam.REDIRECT_URL, REDIRECT_URI);
        return config;
    }

    private static InoreaderAdaptor inoreaderAdaptor(String accessToken) {
        return inoreaderAdaptor(accessToken, null);
    }

    private static InoreaderAdaptor inoreaderAdaptor(String accessToken, String refreshToken) {
        Token token = Token.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
        return new InoreaderAdaptor(token, config());
    }


    @Test
    @Ignore("elo")
    public void test() {
        Map<InoreaderAdaptor.InoreaderParam, String> config = new HashMap<>();
        config.put(InoreaderAdaptor.InoreaderParam.URL, "https://www.inoreader.com/reader/api/0");
        config.put(InoreaderAdaptor.InoreaderParam.AUTH_URL, "https://www.inoreader.com/oauth2/token");
        config.put(InoreaderAdaptor.InoreaderParam.CLIENT_ID, "1000001083");
        config.put(InoreaderAdaptor.InoreaderParam.CLIENT_SECRET, "LiFY_ZeWCm70HT62kN17wnQlki3BjJtX");
        config.put(InoreaderAdaptor.InoreaderParam.REDIRECT_URL, "https://app.keendly.com/inoreaderCallback");

        Token token = Token.builder()
            .accessToken("59e89519152c8e44a4a7f3494202379c24354a6e")
            .refreshToken("9dc176bc35c7a5b7300e8831c7cc05fd0fff091d")
            .build();

        InoreaderAdaptor adaptor = new InoreaderAdaptor(token, config);

        List<ExternalFeed> feeds = adaptor.getFeeds();

        DbUtils.Environment environment = DbUtils.Environment.builder()
            .url("jdbc:postgresql://keendly.cw2niifxhbyl.eu-west-1.rds.amazonaws.com:5432/keendly")
            .password("jA5iyUWsjqqz_viIc7n6")
            .user("keendly")
            .build();

        DeliveryDao deliveryDAO = new DeliveryDao(environment);

        List<String> feedsToMarkAsRead = new ArrayList<>();
        Delivery stored = deliveryDAO.findById(593189l);
        for (DeliveryItem item : stored.getItems()) {
            if (item.getMarkAsRead()) {
                feedsToMarkAsRead.add(item.getFeedId());
            }
        }

        if (!feedsToMarkAsRead.isEmpty()) {
            adaptor.markFeedRead(feedsToMarkAsRead, 1523601522251l);
        }
    }
}
