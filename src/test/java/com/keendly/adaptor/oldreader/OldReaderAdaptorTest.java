package com.keendly.adaptor.oldreader;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.*;
import static com.keendly.adaptor.AssertHelpers.*;
import static java.util.Arrays.*;
import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.adaptor.model.auth.Token;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class OldReaderAdaptorTest {

    private static final int PORT = 8089;
    private static final String RESOURCES = "src/test/resources";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig()
            .port(PORT)
            .fileSource(new SingleRootFileSource(RESOURCES))
    );

    @Test
    public void given_ResponseOK_when_login_then_ReturnToken() throws Exception {
        String USERNAME = "dummy_user";
        String PASSWORD = "dummy_pass";
        String ACCESS_TOKEN = "atam";

        // given
        stubFor(post(urlEqualTo("/auth"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("oldreader/given_ResponseOK_when_login_then_ReturnToken")));

        // when
        Credentials credentials = new Credentials();
        credentials.setUsername(USERNAME);
        credentials.setPassword(PASSWORD);

        OldReaderAdaptor adaptor = new OldReaderAdaptor(credentials, config());
        Token token = adaptor.getToken();

        // then
        assertEquals(ACCESS_TOKEN, token.getAccessToken());

        verify(postRequestedFor(urlEqualTo("/auth"))
                .withRequestBody(thatContainsParams(
                        param("client", "Keendly"),
                        param("accountType", "HOSTED_OR_GOOGLE"),
                        param("service", "reader"),
                        param("Email", USERNAME),
                        param("Passwd", PASSWORD)))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));
    }

    @Test
    public void given_ResponseOK_when_markAsRead_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String FEED_ID1 = "feed/56afe6ac091452684a00048f";
        String FEED_ID2 = "feed/56afe6af091452684a0004b3";
        long timestamp = System.currentTimeMillis();

        // given
        givenThat(post(urlEqualTo("/mark-all-as-read"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = oldReaderAdaptor(ACCESS_TOKEN)
                .markFeedRead(asList(FEED_ID1, FEED_ID2), timestamp);

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/mark-all-as-read"))
                .withRequestBody(thatContainsParams(
                        param("s", FEED_ID1),
                        param("ts", Long.toString(timestamp * 1000000))
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("GoogleLogin auth=" + ACCESS_TOKEN)));

        verify(postRequestedFor(urlPathEqualTo("/mark-all-as-read"))
                .withRequestBody(thatContainsParams(
                        param("s", FEED_ID2),
                        param("ts", Long.toString(timestamp * 1000000))
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("GoogleLogin auth=" + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_markArticleRead_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "tag:google.com,2005:reader/item/5804dcd8175ad6ee4f01495f";
        String ARTICLE_ID2 = "tag:google.com,2005:reader/item/58041076175ad6ca9f000ff1";

        // given
        givenThat(post(urlMatching("/edit-tag.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = oldReaderAdaptor(ACCESS_TOKEN)
                .markArticleRead(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/edit-tag"))
                .withRequestBody(thatContainsParams(
                        param("a", "user/-/state/com.google/read"),
                        param("i", ARTICLE_ID1),
                        param("i", ARTICLE_ID2)
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("GoogleLogin auth=" + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_markArticleUnread_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "tag:google.com,2005:reader/item/5804dcd8175ad6ee4f01495f";
        String ARTICLE_ID2 = "tag:google.com,2005:reader/item/58041076175ad6ca9f000ff1";

        // given
        givenThat(post(urlMatching("/edit-tag.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = oldReaderAdaptor(ACCESS_TOKEN)
                .markArticleUnread(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/edit-tag"))
                .withRequestBody(thatContainsParams(
                        param("r", "user/-/state/com.google/read"),
                        param("i", ARTICLE_ID1),
                        param("i", ARTICLE_ID2)
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("GoogleLogin auth=" + ACCESS_TOKEN)));
    }

    @Test
    public void given_ResponseOK_when_saveArticle_then_ReturnSuccess() throws Exception {
        String ACCESS_TOKEN = "my_token";
        String ARTICLE_ID1 = "tag:google.com,2005:reader/item/5804dcd8175ad6ee4f01495f";
        String ARTICLE_ID2 = "tag:google.com,2005:reader/item/58041076175ad6ca9f000ff1";

        // given
        givenThat(post(urlMatching("/edit-tag.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // when
        boolean success = oldReaderAdaptor(ACCESS_TOKEN)
                .saveArticle(asList(ARTICLE_ID1, ARTICLE_ID2));

        // then
        assertTrue(success);

        verify(postRequestedFor(urlPathEqualTo("/edit-tag"))
                .withRequestBody(thatContainsParams(
                        param("a", "user/-/state/com.google/starred"),
                        param("i", ARTICLE_ID1),
                        param("i", ARTICLE_ID2)
                ))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo("GoogleLogin auth=" + ACCESS_TOKEN)));
    }

    private static Map<OldReaderAdaptor.OldReaderParam, String> config(){
        Map<OldReaderAdaptor.OldReaderParam, String> config = new HashMap<>();
        config.put(OldReaderAdaptor.OldReaderParam.URL, "http://localhost:" + PORT);
        config.put(OldReaderAdaptor.OldReaderParam.AUTH_URL, "http://localhost:" + PORT + "/auth");
        return config;
    }

    private static OldReaderAdaptor oldReaderAdaptor(String accessToken){
        Token token = Token.builder()
            .accessToken(accessToken)
            .build();
        return new OldReaderAdaptor(token, config());
    }
}
