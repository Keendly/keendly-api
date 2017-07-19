package com.keendly.adaptor.newsblur;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.*;
import static com.keendly.adaptor.AssertHelpers.*;
import static java.util.Arrays.*;
import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.keendly.adaptor.exception.ApiException;
import com.keendly.adaptor.model.ExternalFeed;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;
import net.minidev.json.JSONObject;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewsblurAdaptorTest {

    private static final int PORT = 8089;
    private static final String RESOURCES = "src/test/resources";

    private static final String CLIENT_ID = "test";
    private static final String CLIENT_SECRET = "test2";
    private static final String REDIRECT_URI = "redirect_uri";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig()
            .port(PORT)
            .fileSource(new SingleRootFileSource(RESOURCES))
    );

    @Test
    public void given_ResponseOK_when_login_then_ReturnToken() throws Exception {
        String AUTHORIZATION_CODE = "dummy_auth_code";
        String ACCESS_TOKEN = "dummy_access_token";
        String REFRESH_TOKEN = "dummy_refresh_token";

        // given
        stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("newsblur/given_ResponseOK_when_login_then_ReturnToken.json")));

        // when
        Credentials credentials = new Credentials();
        credentials.setAuthorizationCode(AUTHORIZATION_CODE);

        NewsblurAdaptor adaptor = new NewsblurAdaptor(credentials, config());
        Token token = adaptor.getToken();

        // then
        assertEquals(ACCESS_TOKEN, token.getAccessToken());
        assertEquals(REFRESH_TOKEN, token.getRefreshToken());

        verify(postRequestedFor(urlEqualTo("/oauth/token"))
                .withRequestBody(thatContainsParams(
                        param("code", AUTHORIZATION_CODE),
                        param("redirect_uri", REDIRECT_URI),
                        param("client_id", CLIENT_ID),
                        param("client_secret", CLIENT_SECRET),
                        param("grant_type", "authorization_code")))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));
    }

    @Test
    public void given_Error_when_login_then_ThrowException() throws Exception {
        // given
        givenThat(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBodyFile("newsblur/given_Error_when_login_then_ThrowException.json")));

        // when
        Exception thrown = null;
        try {
            newsblurAdaptor(null).login(new Credentials());
        } catch (Exception e){
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(401, ((ApiException) thrown).getStatus());
        assertTrue(((ApiException) thrown).getResponse().contains("invalid_grant"));
    }

    @Test
    public void given_ResponseOK_when_getUser_then_ReturnUser() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String USER_ID = "1001921515";
        String USER_NAME = "moomeen";
        String USER_EMAIL = "moomeen@gmail.com";

        // given
        givenThat(get(urlEqualTo("/social/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getUser_then_ReturnUser.json")));

        givenThat(get(urlEqualTo("/profile/payment_history"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getUser_then_ReturnUser_email.json")));

        // when
        ExternalUser user = newsblurAdaptor(ACCESS_TOKEN).getUser();

        // then
        assertEquals(USER_ID, user.getId());
        assertEquals(USER_NAME, user.getDisplayName());
        assertEquals(USER_EMAIL, user.getUserName());

        verify(getRequestedFor(urlMatching("/social/profile"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlMatching("/profile/payment_history"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_EmailGetError_when_getUser_then_ReturnOnlyUsername() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String USER_ID = "1001921515";
        String USER_NAME = "moomeen";

        // given
        givenThat(get(urlEqualTo("/social/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getUser_then_ReturnUser.json")));

        givenThat(get(urlEqualTo("/profile/payment_history"))
                .willReturn(aResponse()
                        .withStatus(500)));

        // when
        ExternalUser user = newsblurAdaptor(ACCESS_TOKEN).getUser();

        // then
        assertEquals(USER_ID, user.getId());
        assertEquals(USER_NAME, user.getDisplayName());
        assertEquals(USER_NAME, user.getUserName());

        verify(getRequestedFor(urlMatching("/social/profile"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlMatching("/profile/payment_history"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    @Ignore("access token is valid for 3650 days")
    public void given_Unauthorized_when_getUser_then_RefreshTokenAndRetry() throws Exception {
        fail();
    }

    @Test
    @Ignore("access token is valid for 3650 days")
    public void given_RefreshError_when_getUser_then_ThrowException() throws Exception {
        fail();
    }

    @Test
    public void given_NotAuthenticated_when_getUser_then_ThrowException() throws Exception {
        String ACCESS_TOKEN = "my_token";

        // given
        givenThat(get(urlEqualTo("/social/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_NotAuthenticated_when_getUser_then_ThrowException.json")));

        // when
        Exception thrown = null;
        try {
            newsblurAdaptor(ACCESS_TOKEN).getUser();
        } catch (Exception e){
            thrown = e;
        }

        // then
        assertNotNull(thrown);
        assertEquals(401, ((ApiException) thrown).getStatus());
        assertEquals("not authenticated", ((ApiException) thrown).getResponse());

        verify(getRequestedFor(urlMatching("/social/profile"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_Error_when_getUser_then_ThrowException() throws Exception {
        int ERROR_STATUS_CODE = 404;
        String RESPONSE = "error";

        // given
        givenThat(get(urlEqualTo("/social/profile"))
                .willReturn(aResponse()
                        .withStatus(ERROR_STATUS_CODE)
                        .withBody(RESPONSE)));

        // when
        Exception thrown = null;
        try {
            newsblurAdaptor(null).getUser();
        } catch (Exception e){
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
        String FEED_ID1 = "1573179";
        int UNREAD_COUNT1 = 93;
        String FEED_ID2 = "2665372";
        int UNREAD_COUNT2 = 6;

        // given
        givenThat(get(urlEqualTo("/reader/refresh_feeds"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getUnreadCount_then_ReturnUnreadCount.json")));

        // when
        Map<String, Integer> unreadCount =
                newsblurAdaptor(ACCESS_TOKEN).getUnreadCount(asList(FEED_ID1, FEED_ID2));

        // then
        assertEquals(UNREAD_COUNT1, unreadCount.get(FEED_ID1).intValue());
        assertEquals(UNREAD_COUNT2, unreadCount.get(FEED_ID2).intValue());

        verify(getRequestedFor(urlMatching("/reader/refresh_feeds"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_getUnread_then_ReturnUnreadItems() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "feed_id";

        String HASH1 = "1573179:6ac145";
        String TITLE1 = "La MSN batirá su propio récord de goles";
        String AUTHOR1 = "test author";
        int PUBLISHED1 = 1461603529;
        String URL1 = "http://www.sport.es/es/noticias/barca/msn-batira-propio-record-goles-5084039?utm_source=rss-noticias&utm_medium=feed&utm_campaign=barca";
        String CONTENT1 = "test_content";

        // given
        givenThat(get(urlMatching("/reader/feed/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getUnread_then_ReturnUnreadItems.json")));

        givenThat(get(urlEqualTo("/reader/refresh_feeds"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(unreadCountResponse(FEED_ID, 6))));

        // when
        Map<String, List<FeedEntry>> unread =
                newsblurAdaptor(ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));
        assertEquals(6, unread.get(FEED_ID).size());
        assertEntryCorrect(unread.get(FEED_ID).get(0), HASH1, TITLE1, AUTHOR1, PUBLISHED1, URL1, CONTENT1);

        verify(getRequestedFor(urlPathEqualTo("/reader/feed/" + FEED_ID))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlEqualTo("/reader/refresh_feeds"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_IdNotUrl_when_getUnread_then_ReturnPermalinkAsUrl() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "5149478";

        String URL1 = "http://feedproxy.google.com/~r/AmazonWebServicesBlog/~3/49pMIkRfTWY/";

        // given
        givenThat(get(urlMatching("/reader/feed/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_IdNotUrl_when_getUnread_then_ReturnPermalinkAsUrl.json")));

        givenThat(get(urlEqualTo("/reader/refresh_feeds"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(unreadCountResponse(FEED_ID, 6))));

        // when
        Map<String, List<FeedEntry>> unread =
                newsblurAdaptor(ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));
        assertEquals(URL1, (unread.get(FEED_ID).get(0).getUrl()));
    }

    @Test
    public void given_MoreResults_when_getUnread_then_FetchNextPage() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID = "feed_id";

        String HASH7 = "1573179:b5e660";
        String TITLE7 = "Cristiano Ronaldo solo gana a Luis Suárez en penaltis";
        String AUTHOR7 = "";
        int PUBLISHED7 = 1461594683;
        String URL7 = "http://www.sport.es/es/noticias/barca/cristiano-ronaldo-solo-gana-luis-suarez-penaltis-5083812?utm_source=rss-noticias&utm_medium=feed&utm_campaign=barca";
        String CONTENT7 = "test_content2";

        // given
        givenThat(get(urlMatching("/reader/feed/.*")).inScenario("Many pages")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getUnread_then_ReturnUnreadItems.json"))
                .willSetStateTo("First page fetched"));

        givenThat(get(urlMatching("/reader/feed/.*")).inScenario("Many pages")
                .whenScenarioStateIs("First page fetched")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getUnread_then_ReturnUnreadItems_page2.json")));

        givenThat(get(urlEqualTo("/reader/refresh_feeds"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(unreadCountResponse(FEED_ID, 12))));

        // when
        Map<String, List<FeedEntry>> unread =
                newsblurAdaptor(ACCESS_TOKEN).getUnread(asList(FEED_ID));

        // then
        assertTrue(unread.containsKey(FEED_ID));
        assertEquals(12, unread.get(FEED_ID).size());
        assertEntryCorrect(unread.get(FEED_ID).get(6), HASH7, TITLE7, AUTHOR7, PUBLISHED7, URL7, CONTENT7);

        verify(getRequestedFor(urlPathEqualTo("/reader/feed/" + FEED_ID))
                .withQueryParam("page", equalTo("1"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlPathEqualTo("/reader/feed/" + FEED_ID))
                .withQueryParam("page", equalTo("2"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(getRequestedFor(urlEqualTo("/reader/refresh_feeds"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_getFeeds_then_ReturnFeeds() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID1 = "834";
        String FEED_TITLE1 = "Giant Robots Smashing Into Other Giant Robots";
        String FEED_CATEGORY1 = "software";
        String FEED_ID2 = "2614";
        String FEED_TITLE2 = "AWS Official Blog";
        String FEED_CATEGORY2 = "software";

        // given
        givenThat(get(urlEqualTo("/reader/feeds"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("newsblur/given_ResponseOK_when_getFeeds_then_ReturnFeeds.json")));

        // when
        List<ExternalFeed> feeds = newsblurAdaptor(ACCESS_TOKEN).getFeeds();

        // then
        assertEquals(26, feeds.size());
        assertFeedCorrect(feeds.get(0), FEED_TITLE1, FEED_ID1, FEED_CATEGORY1);
        assertFeedCorrect(feeds.get(1), FEED_TITLE2, FEED_ID2, FEED_CATEGORY2);

        verify(getRequestedFor(urlPathEqualTo("/reader/feeds"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_markAsRead_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID1 = "6297529";
        String FEED_ID2 = "6297523";
        long timestamp = System.currentTimeMillis();

        // given
        givenThat(post(urlEqualTo("/reader/mark_feed_as_read"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = newsblurAdaptor(ACCESS_TOKEN)
                .markFeedRead(asList(FEED_ID1, FEED_ID2), timestamp);

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/reader/mark_feed_as_read"))
                .withRequestBody(thatContainsParams(
                        param("feed_id", FEED_ID1),
                        param("cutoff_timestamp", Long.toString(timestamp / 1000))
                ))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        verify(postRequestedFor(urlPathEqualTo("/reader/mark_feed_as_read"))
                .withRequestBody(thatContainsParams(
                        param("feed_id", FEED_ID2),
                        param("cutoff_timestamp", Long.toString(timestamp / 1000))
                ))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_markArticleRead_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "1573179:62ec2d";
        String ARTICLE_ID2 = "1573179:0f6e60";

        // given
        givenThat(post(urlMatching("/reader/mark_story_hashes_as_read.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = newsblurAdaptor(ACCESS_TOKEN)
                .markArticleRead(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/reader/mark_story_hashes_as_read"))
                .withRequestBody(thatContainsParams(
                        param("story_hash", ARTICLE_ID1),
                        param("story_hash", ARTICLE_ID2)
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_markArticleUnread_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "1573179:62ec2d";
        String ARTICLE_ID2 = "1573179:0f6e60";

        // given
        givenThat(post(urlMatching("/reader/mark_story_hash_as_unread.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = newsblurAdaptor(ACCESS_TOKEN)
                .markArticleUnread(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/reader/mark_story_hash_as_unread"))
                .withRequestBody(thatContainsParams(
                        param("story_hash", ARTICLE_ID1),
                        param("story_hash", ARTICLE_ID2)
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_saveArticle_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "1573179:62ec2d";
        String ARTICLE_ID2 = "1573179:0f6e60";

        // given
        givenThat(post(urlMatching("/reader/mark_story_hash_as_starred.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = newsblurAdaptor(ACCESS_TOKEN)
                .saveArticle(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/reader/mark_story_hash_as_starred"))
                .withRequestBody(thatContainsParams(
                        param("story_hash", ARTICLE_ID1),
                        param("story_hash", ARTICLE_ID2)
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

//    @Test
//    public void given_ResponseOK_when_getArticles_then_ReturnArticles() throws Exception {
//        String ACCESS_TOKEN = "my_token";
//        String ARTICLE_ID1 = "1573179:f94f7b";
//        String ARTICLE_TITLE1 = "La noche en la que Guardiola alucinó con Messi";
//        String ARTICLE_ID2 = "834:433999";
//        String ARTICLE_TITLE2 = "Acceptance Tests with Subdomains";
//
//        // given
//        givenThat(get(urlMatching("/reader/river_stories.*"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withBodyFile("newsblur/given_ResponseOK_when_getArticles_then_ReturnArticles.json")));
//
//        // when
//        List<FeedEntry> articles = newsblurAdaptor(ACCESS_TOKEN)
//                .getArticles(Arrays.asList(ARTICLE_ID1, ARTICLE_ID2))
//                .get(1000);
//
//        // then
//        assertEquals(2, articles.size());
//        assertEquals(ARTICLE_ID1, articles.get(0).getId());
//        assertEquals(ARTICLE_TITLE1, articles.get(0).getTitle());
//        assertEquals(ARTICLE_ID2, articles.get(1).getId());
//        assertEquals(ARTICLE_TITLE2, articles.get(1).getTitle());
//
//        verify(getRequestedFor(urlPathEqualTo("/reader/river_stories"))
//                .withQueryParam("h", equalTo(ARTICLE_ID1))
//                .withQueryParam("h", equalTo(ARTICLE_ID2))
//                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
//    }

    private String unreadCountResponse(String feedId, int count) {
        JSONObject unreadCountResponse = new JSONObject();
        JSONObject feedsUnreadCounts = new JSONObject();
        JSONObject feedUnread = new JSONObject();
        feedUnread.put("nt", count);
        feedsUnreadCounts.put(feedId, feedUnread);
        unreadCountResponse.put("feeds", feedsUnreadCounts);
        return unreadCountResponse.toString();
    }

    private static Map<NewsblurAdaptor.NewsblurParam, String> config(){
        Map<NewsblurAdaptor.NewsblurParam, String> config = new HashMap<>();
        config.put(NewsblurAdaptor.NewsblurParam.URL, "http://localhost:" + PORT);
        config.put(NewsblurAdaptor.NewsblurParam.CLIENT_ID, CLIENT_ID);
        config.put(NewsblurAdaptor.NewsblurParam.CLIENT_SECRET, CLIENT_SECRET);
        config.put(NewsblurAdaptor.NewsblurParam.REDIRECT_URL, REDIRECT_URI);
        return config;
    }

    private static NewsblurAdaptor newsblurAdaptor(String accessToken){
        return newsblurAdaptor(accessToken, null);
    }

    private static NewsblurAdaptor newsblurAdaptor(String accessToken, String refreshToken){
        Token token = Token.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
        return new NewsblurAdaptor(token, config());
    }
}
