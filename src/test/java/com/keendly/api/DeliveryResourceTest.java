package com.keendly.api;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.keendly.adaptor.Adaptor;
import com.keendly.adaptor.AdaptorFactory;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.dao.DeliveryDao;
import com.keendly.dao.UserDao;
import com.keendly.model.Delivery;
import com.keendly.model.DeliveryItem;
import com.keendly.model.Provider;
import com.keendly.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class DeliveryResourceTest {

    private static Long USER_ID = 1L;
    
    private SecurityContext securityContext = mock(SecurityContext.class);
    private UserDao userDao = mock(UserDao.class);
    private DeliveryDao deliveryDao = mock(DeliveryDao.class);
    private AmazonS3 amazonS3 = mock(AmazonS3.class);
    private AWSStepFunctions awsStepFunctions = mock(AWSStepFunctions.class);
    
    private Adaptor adaptor = mock(Adaptor.class);
    
    private DeliveryResource deliveryResource = 
        new DeliveryResource(deliveryDao, userDao, amazonS3, awsStepFunctions);
    
    @Before
    public void setUp() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(USER_ID.toString());
        when(securityContext.getUserPrincipal()).thenReturn(principal);

        PowerMockito.mockStatic(AdaptorFactory.class);
        PowerMockito.when(AdaptorFactory.getInstance(any(User.class))).thenReturn(adaptor);
        
//        AdaptorFactory.setAdaptor(Provider.INOREADER, adaptor.getClass());
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
    public void given_deliverySenderEmailNotConfigured_when_createDelivery_then_returnError() {
        // given
        when(userDao.findById(eq(USER_ID))).thenReturn(
            User.builder()
                .deliveryEmail("blabla@kindle.com")
                .build());

        // when
        Response response = createDelivery(Delivery.builder().build());

        // then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("DELIVERY_SENDER_NOT_SET", ((Map) response.getEntity()).get("code"));
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
    public void given_tooManyArticles_when_createDelivery_then_limitArticlesReturned() {
        // given

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
        assertEquals("TOO_MANY_ITEMS", ((Map) response.getEntity()).get("code"));
    }

    @Test
    public void given_noArticlesAndNotManual_when_createDelivery_then_storeError() {
        // given

    }

    @Test
    public void given_noArticlesAndNotManualAndNotifyNoArticles_when_createDelivery_then_sendNotifyEmail() {
        // given

    }
    
    private Response createDelivery(Delivery delivery) {
        return deliveryResource.createDelivery(securityContext, delivery);
    }
}
