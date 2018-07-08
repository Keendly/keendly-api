package com.keendly.api;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.StartExecutionResult;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keendly.adaptor.Adaptor;
import com.keendly.adaptor.AdaptorFactory;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.dao.DeliveryDao;
import com.keendly.dao.UserDao;
import com.keendly.model.Delivery;
import com.keendly.model.DeliveryItem;
import com.keendly.model.Premium;
import com.keendly.model.Provider;
import com.keendly.model.Subscription;
import com.keendly.model.User;
import com.keendly.perun.PerunRequest;
import com.keendly.perun.PerunService;
import com.keendly.premium.PremiumUtils;
import com.keendly.push_notifier.PushNotifierRequest;
import com.keendly.push_notifier.PushNotifierService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AdaptorFactory.class, PremiumUtils.class})
public class DeliveryResourceTest {

    private static Long USER_ID = 1L;
    
    private SecurityContext securityContext = mock(SecurityContext.class);
    private UserDao userDao = mock(UserDao.class);
    private DeliveryDao deliveryDao = mock(DeliveryDao.class);
    private AmazonS3 amazonS3 = mock(AmazonS3.class);
    private AWSStepFunctions awsStepFunctions = mock(AWSStepFunctions.class);
    private PerunService perunService = mock(PerunService.class);
    private PushNotifierService pushNotifierService = mock(PushNotifierService.class);
    
    private Adaptor adaptor = mock(Adaptor.class);
    
    private DeliveryResource deliveryResource = 
        new DeliveryResource(deliveryDao, userDao, amazonS3, awsStepFunctions, perunService, pushNotifierService);
    
    @Before
    public void setUp() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(USER_ID.toString());
        when(securityContext.getUserPrincipal()).thenReturn(principal);

        PowerMockito.mockStatic(AdaptorFactory.class);
        PowerMockito.when(AdaptorFactory.getInstance(any(User.class))).thenReturn(adaptor);

        PowerMockito.mockStatic(PremiumUtils.class);
        PowerMockito.when(PremiumUtils.getPremiumStatus(any(User.class)))
            .thenReturn(Premium.builder()
                .active(true)
                .build());
    }
    
    @Test
    public void when_createDelivery_then_create() {

    }

    @Test
    public void given_deliveryEmailNotConfigured_when_createDelivery_then_returnError() {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(User.builder().build());

        // when
        Response response = createDelivery(Delivery.builder().build());

        // then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("DELIVERY_EMAIL_NOT_CONFIGURED", ((Map) response.getEntity()).get("code"));
    }

    @Test
    public void given_tooManyFeeds_when_createDelivery_then_returnError() {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .build());
        
        // when
        List<DeliveryItem> items = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            items.add(DeliveryItem.builder().build());
        }
        Response response = createDelivery(Delivery.builder().items(items).build());

        // then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("TOO_MANY_ITEMS", ((Map) response.getEntity()).get("code"));
    }

    @Test
    public void given_tooManyArticles_when_createDelivery_then_limitArticlesReturned() throws IOException {
        int ARTICLES_COUNT = 600;
        int ARTICLES_LIMIT = 500;

        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .provider(Provider.INOREADER)
                .build());

        Map<String, List<FeedEntry>> unread = new HashMap<>();
        unread.put("feed/http://brodatyblog.pl/feed/atom/", generateArticles(ARTICLES_COUNT));
        when(adaptor.getUnread(any())).thenReturn(unread);

        StartExecutionResult executionStart = mock(StartExecutionResult.class);
        when(executionStart.getExecutionArn()).thenReturn("dummyExecutionArn");
        when(awsStepFunctions.startExecution(any())).thenReturn(executionStart);

        // when
        DeliveryItem feed = DeliveryItem.builder()
            .feedId("feed/http://brodatyblog.pl/feed/atom/")
            .build();
        Response response = createDelivery(Delivery.builder()
            .items(Arrays.asList(feed))
            .manual(true)
            .build());

        // then
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        List<DeliveryItem> stored = storedDeliveryItems();
        assertEquals(ARTICLES_LIMIT, stored.get(0).getArticles().size());
    }

    @Test
    public void given_noArticlesInFeed_when_createDelivery_then_skipFeed() throws IOException {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .provider(Provider.INOREADER)
                .build());

        Map<String, List<FeedEntry>> unread = new HashMap<>();
        unread.put("feed/http://brodatyblog.pl/feed/atom/", generateArticles(1));
        unread.put("feed/http://feeds2.feedburner.com/24thfloor", Collections.emptyList());
        when(adaptor.getUnread(any())).thenReturn(unread);

        StartExecutionResult executionStart = mock(StartExecutionResult.class);
        when(executionStart.getExecutionArn()).thenReturn("dummyExecutionArn");
        when(awsStepFunctions.startExecution(any())).thenReturn(executionStart);

        // when
        DeliveryItem feed = DeliveryItem.builder()
            .feedId("feed/http://brodatyblog.pl/feed/atom/")
            .build();
        DeliveryItem feed2 = DeliveryItem.builder()
            .feedId("feed/http://feeds2.feedburner.com/24thfloor")
            .build();
        Response response = createDelivery(Delivery.builder()
            .items(Arrays.asList(feed, feed2))
            .manual(true)
            .build());

        // then
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        List<DeliveryItem> stored = storedDeliveryItems();
        assertEquals(1, stored.size());
    }

    private List<DeliveryItem> storedDeliveryItems() throws IOException {
        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
        verify(amazonS3).putObject(anyString(), anyString(), is.capture(), any());
        ObjectMapper mapper = new ObjectMapper();
        return  mapper.readValue(IOUtils.toByteArray(is.getValue()),
            new TypeReference<List<DeliveryItem>>() {});
    }

    private static List<FeedEntry> generateArticles(int number) {
        List<FeedEntry> articles = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            FeedEntry article = new FeedEntry();
            article.setId(i + "");
            article.setPublished(new Date());
            articles.add(article);
        }
        return articles;
    }

    @Test
    public void given_noArticlesAndManual_when_createDelivery_then_returnError() {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .provider(Provider.INOREADER)
                .build());
        
        Map<String, List<FeedEntry>> unread = new HashMap<>();
        unread.put("feed/http://brodatyblog.pl/feed/atom/", Collections.emptyList());
        when(adaptor.getUnread(any())).thenReturn(unread);

        // when
        DeliveryItem feed = DeliveryItem.builder()
            .feedId("feed/http://brodatyblog.pl/feed/atom/")
            .build();
        Response response = createDelivery(Delivery.builder()
            .items(Arrays.asList(feed))
            .manual(true)
            .build());

        // then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("NO_ARTICLES", ((Map) response.getEntity()).get("code"));
    }

    @Test
    public void given_noArticlesAndNotManual_when_createDelivery_then_storeError() {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .provider(Provider.INOREADER)
                .notifyNoArticles(false)
                .build());

        Map<String, List<FeedEntry>> unread = new HashMap<>();
        unread.put("feed/http://brodatyblog.pl/feed/atom/", Collections.emptyList());
        when(adaptor.getUnread(any())).thenReturn(unread);

        // when
        DeliveryItem feed = DeliveryItem.builder()
            .feedId("feed/http://brodatyblog.pl/feed/atom/")
            .build();
        Response response = createDelivery(Delivery.builder()
            .items(Arrays.asList(feed))
            .subscription(Subscription.builder().id(1L).build())
            .manual(false)
            .build());

        // then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("NO ARTICLES", ((Delivery) response.getEntity()).getError());
        ArgumentCaptor<Delivery> deliveryArgumentCaptor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveryDao).createDelivery(deliveryArgumentCaptor.capture(), eq(USER_ID));
        assertEquals("NO ARTICLES", deliveryArgumentCaptor.getValue().getError());
        assertEquals(1L, deliveryArgumentCaptor.getValue().getSubscription().getId().longValue());
    }

    @Test
    public void given_noArticlesAndNotManualAndNotifyNoArticles_when_createDelivery_then_sendNotifyEmail() {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .email("user@mail.com")
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .provider(Provider.INOREADER)
                .notifyNoArticles(true)
                .build());

        Map<String, List<FeedEntry>> unread = new HashMap<>();
        unread.put("feed/http://brodatyblog.pl/feed/atom/", Collections.emptyList());
        when(adaptor.getUnread(any())).thenReturn(unread);

        // when
        DeliveryItem feed = DeliveryItem.builder()
            .feedId("feed/http://brodatyblog.pl/feed/atom/")
            .build();
        Response response = createDelivery(Delivery.builder()
            .items(Arrays.asList(feed))
            .subscription(Subscription.builder().id(1L).build())
            .manual(false)
            .build());

        // then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ArgumentCaptor<PerunRequest> perunRequestArgumentCaptor = ArgumentCaptor.forClass(PerunRequest.class);
        verify(perunService).sendEmail(perunRequestArgumentCaptor.capture());
        assertEquals("user@mail.com", perunRequestArgumentCaptor.getValue().getRecipient());
    }

    @Test
    public void given_noArticlesAndNotManualAndNotifyNoArticles_when_createDelivery_then_sendPushNotification() {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .email("user@mail.com")
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .provider(Provider.INOREADER)
                .notifyNoArticles(true)
                .build());

        Map<String, List<FeedEntry>> unread = new HashMap<>();
        unread.put("feed/http://brodatyblog.pl/feed/atom/", Collections.emptyList());
        when(adaptor.getUnread(any())).thenReturn(unread);

        // when
        DeliveryItem feed = DeliveryItem.builder()
            .feedId("feed/http://brodatyblog.pl/feed/atom/")
            .build();
        Response response = createDelivery(Delivery.builder()
            .items(Arrays.asList(feed))
            .subscription(Subscription.builder().id(1L).build())
            .manual(false)
            .build());

        // then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ArgumentCaptor<PushNotifierRequest> pushRequestArgumentCaptor = ArgumentCaptor.forClass(PushNotifierRequest.class);
        verify(pushNotifierService).sendNotification(pushRequestArgumentCaptor.capture());
        assertEquals(USER_ID, pushRequestArgumentCaptor.getValue().getUserId());
    }

    @Test
    public void given_notManualDeliverAndNotPremiumUser_when_createDelivery_then_returnError() {
        // given
        PowerMockito.when(PremiumUtils.getPremiumStatus(any(User.class)))
            .thenReturn(Premium.builder()
                .active(false)
                .build());

        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .deliveryEmail("blabla@kindle.com")
                .deliverySender("blabla@keendly.com")
                .provider(Provider.INOREADER)
                .build());

        // when
        DeliveryItem feed = DeliveryItem.builder()
            .feedId("feed/http://brodatyblog.pl/feed/atom/")
            .build();
        Response response = createDelivery(Delivery.builder()
            .items(Arrays.asList(feed))
            .manual(false)
            .build());

        // then
        assertEquals(Response.Status.PAYMENT_REQUIRED.getStatusCode(), response.getStatus());
        assertEquals("NO_PREMIUM", ((Map) response.getEntity()).get("code"));
    }

    private Response createDelivery(Delivery delivery) {
        return deliveryResource.createDelivery(securityContext, delivery);
    }
}
