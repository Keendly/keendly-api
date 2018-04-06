package com.keendly.api;

import static com.keendly.utils.ConfigUtils.*;

import com.amazonaws.util.StringUtils;
import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenRequest;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Environment;
import com.braintreegateway.PaymentMethod;
import com.braintreegateway.PaymentMethodRequest;
import com.braintreegateway.Result;
import com.braintreegateway.Subscription;
import com.braintreegateway.SubscriptionRequest;
import com.braintreegateway.exceptions.NotFoundException;
import com.keendly.dao.UserDao;
import com.keendly.model.PremiumRequest;
import com.keendly.model.PushSubscription;
import com.keendly.model.User;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("/users")
public class UserResource {

    private static final String[] ALLOWED_DOMAINS = {"kindle.com", "free.kindle.com", "kindle.cn", "pbsync.com"};

    private UserDao userDAO = new UserDao();
    private BraintreeGateway gateway = BraintreeGateway.forPartner(
        parameter("BRAINTREE_ENV").equals("PRODUCTION") ? Environment.PRODUCTION : Environment.SANDBOX,
        parameter("BRAINTREE_PARTNER_ID"),
        parameter("BRAINTREE_PUBLIC_KEY"),
        parameter("BRAINTREE_PRIVATE_KEY"));

    @GET
    @Path("/self")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getUser(@Context SecurityContext securityContext) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());

        User user = userDAO.findById(userId);
        return Response.ok(
            User.builder()
                .id(user.getId())
                .provider(user.getProvider())
                .email(user.getEmail())
                .deliveryEmail(user.getDeliveryEmail())
                .deliverySender(user.getDeliverySender())
                .notifyNoArticles(user.getNotifyNoArticles())
                .isPremium(
                    user.getPremiumSubscriptionId() != null &&
                    gateway.subscription().find(user.getPremiumSubscriptionId()).getStatus() == Subscription.Status.ACTIVE
                )
                .pushSubscriptions(user.getPushSubscriptions())
                .build())
            .build();
    }
    
    @PATCH
    @Path("/self")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response updateUser(@Context SecurityContext securityContext, User user) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        User savedUser = userDAO.findById(userId);
        String deliverySender = savedUser.getDeliverySender();

        if (user.getDeliveryEmail() != null) {
            if (!validateDeliveryEmail(user.getDeliveryEmail())) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Error.WRONG_EMAIL.asEntity(StringUtils.join(", ", ALLOWED_DOMAINS)))
                    .build();
            }
            deliverySender = generateSenderEmail(user.getDeliveryEmail());
        }

        userDAO.updateUser(userId, User.builder()
            .deliveryEmail(user.getDeliveryEmail())
            .deliverySender(deliverySender)
            .notifyNoArticles(user.getNotifyNoArticles())
            .build());

        return Response.ok().build();
    }

    @GET
    @Path("/self/token")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getClientToken(@Context SecurityContext securityContext) {
        String token = gateway.clientToken()
            .generate(new ClientTokenRequest().customerId(securityContext.getUserPrincipal().getName()));

        return Response.ok(token).build();
    }

    @POST
    @Path("/self/premium")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response goPremium(@Context SecurityContext securityContext, PremiumRequest request) {
        String userId = securityContext.getUserPrincipal().getName();
        String paymentToken;
        if (!braintreeCustomerExists(userId)) {
            paymentToken = createBraintreeCustomerWithPayment(userId, request.getNonce());
        } else {
            paymentToken = createPayment(userId, request.getNonce());
        }

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest()
            .paymentMethodToken(paymentToken)
            .planId(request.getPlainId());

        Result<Subscription> result = gateway.subscription().create(subscriptionRequest);

        if (result.isSuccess()) {
            String subscriptionId = result.getTarget().getId();
            userDAO.setPremiumSubscriptionId(Long.parseLong(userId), subscriptionId);
        }
        return Response.ok().build();
    }

    @DELETE
    @Path("/self/premium")
    public Response deletePremium(@Context SecurityContext securityContext) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        String subscriptionId = userDAO.findById(userId).getPremiumSubscriptionId();
        Result<Subscription> result = gateway.subscription().cancel(subscriptionId);
        if (result.isSuccess()) {
            userDAO.deletePremiumSubscriptionId(userId);
            return Response.ok().build();
        } else {
            throw new RuntimeException("Couldn't cancel subscription :(");
        }
    }

    @POST
    @Path("/self/pushsubscriptions")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response addPushSubscription(@Context SecurityContext securityContext, PushSubscription subscription) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        Long id = userDAO.addPushSubscription(userId, subscription);
        return Response.status(Response.Status.CREATED)
            .entity(PushSubscription.builder()
                .id(id)
                .build())
            .build();
    }

    @DELETE
    @Path("/self/pushsubscriptions/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response deletePushSubscription(@Context SecurityContext securityContext, @PathParam("id") String id) {
        userDAO.deletePremiumSubscriptionId(Long.valueOf(id));
        return Response.ok().build();
    }

    private boolean validateDeliveryEmail(String email){
        String[] split = email.split("\\@");
        if (split.length != 2){
            return false;
        }
        boolean valid = false;
        for (String allowedDomain : ALLOWED_DOMAINS){
            if (split[1].equals(allowedDomain)){
                valid = true;
                break;
            }
        }
        return valid;
    }

    private String generateSenderEmail(String deliveryEmail){
        String[] split = deliveryEmail.split("\\@");
        return split[0] + "@keendly.com";
    }

    private boolean braintreeCustomerExists(String id) {
        try {
            gateway.customer().find(id);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private String createBraintreeCustomerWithPayment(String userId, String paymentOnce) {
        CustomerRequest request = new CustomerRequest()
            .id(userId)
            .paymentMethodNonce(paymentOnce);
        Result<Customer> result = gateway.customer().create(request);
        if (result.isSuccess()) {
            return result.getTarget().getPaymentMethods().get(0).getToken();
        } else {
            throw new RuntimeException("Couldn't create braintree user :(");
        }
    }

    private String createPayment(String userId, String paymentOnce) {
        PaymentMethodRequest request = new PaymentMethodRequest()
            .customerId(userId)
            .paymentMethodNonce(paymentOnce);

        Result<? extends PaymentMethod> result = gateway.paymentMethod().create(request);

        if (result.isSuccess()) {
            return result.getTarget().getToken();
        } else {
            throw new RuntimeException("Couldn't create braintree payment :(");
        }
    }
}
