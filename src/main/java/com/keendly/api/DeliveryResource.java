package com.keendly.api;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.invoke.LambdaInvokerFactory;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClient;
import com.amazonaws.services.stepfunctions.model.StartExecutionRequest;
import com.amazonaws.services.stepfunctions.model.StartExecutionResult;
import com.amazonaws.util.json.Jackson;
import com.keendly.adaptor.Adaptor;
import com.keendly.adaptor.AdaptorFactory;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.dao.DeliveryDao;
import com.keendly.dao.UserDao;
import com.keendly.model.Delivery;
import com.keendly.model.DeliveryItem;
import com.keendly.model.User;
import com.keendly.states.DeliveryRequest;
import com.keendly.states.Mapper;
import com.keendly.states.S3Object;
import com.keendly.veles.VelesRequest;
import com.keendly.veles.VelesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/deliveries")
public class DeliveryResource {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryResource.class);
    private static int MAX_FEEDS_IN_DELIVERY = 25;
    private static int MAX_ARTICLES_IN_DELIVERY = 500;
    private static String BUCKET = "keendly";
    private static final String STATE_MACHINE_ARN = "arn:aws:states:eu-west-1:625416862388:stateMachine:Delivery5";

    private DeliveryDao deliveryDAO;
    private UserDao userDAO;
    private AmazonS3 amazonS3Client;
    private AWSStepFunctions awsStepFunctionsClient;
    private VelesService velesService;

    public DeliveryResource() {
        this.deliveryDAO = new DeliveryDao();
        this.userDAO = new UserDao();
        this.amazonS3Client = new AmazonS3Client();
        this.awsStepFunctionsClient = getStepFunctionsClient();
        this.velesService = LambdaInvokerFactory.builder()
            .lambdaClient(AWSLambdaClientBuilder.defaultClient())
            .build(VelesService.class);
    }
    
    public DeliveryResource(DeliveryDao deliveryDao, UserDao userDao, 
        AmazonS3 amazonS3, AWSStepFunctions awsStepFunctions, VelesService velesService) {
        this.deliveryDAO = deliveryDao;
        this.userDAO = userDao;
        this.amazonS3Client = amazonS3;
        this.awsStepFunctionsClient = awsStepFunctions;
        this.velesService = velesService;
    }
    
    private AWSStepFunctions getStepFunctionsClient() {
        AWSStepFunctions awsStepFunctionsClient = new AWSStepFunctionsClient();
        awsStepFunctionsClient.setRegion(Region.getRegion(Regions.EU_WEST_1));
        return awsStepFunctionsClient;
    }
    
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getDelivery(@PathParam("id") String id) {
        Delivery d = deliveryDAO.findById(Long.parseLong(id));
        return Response.ok(d).build();
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getDeliveries(@Context SecurityContext securityContext,
        @QueryParam("subscriptionId") String subscriptionId,
        @QueryParam("page") String page,
        @QueryParam("pageSize") String pageSize) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());
        List<Delivery> deliveries;

        if (subscriptionId != null) {
            deliveries = deliveryDAO.getSubscriptionDeliveries(userId, Long.valueOf(subscriptionId));
        } else {
            deliveries =
                deliveryDAO.getDeliveries(userId, Integer.valueOf(page), Integer.valueOf(pageSize));
        }
        return Response.ok(deliveries).build();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createDelivery(@Context SecurityContext securityContext, Delivery delivery) {
        Long userId = Long.valueOf(securityContext.getUserPrincipal().getName());

        // validate user
        User user = userDAO.findById(userId);
        if (user.getDeliveryEmail() == null || user.getDeliveryEmail().isEmpty()) {
            LOG.error("Delivery email not configured for user {}", userId);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Error.DELIVERY_EMAIL_NOT_CONFIGURED.asEntity())
                .build();
        }
        if (user.getDeliverySender() == null || user.getDeliverySender().isEmpty()) {
            LOG.error("Delivery sender not configured for user {}", userId);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Error.DELIVERY_SENDER_NOT_SET.asEntity())
                .build();
        }

        // validate delivery
        if (delivery.getItems().size() > MAX_FEEDS_IN_DELIVERY) {
            LOG.error("Too many ({}) feeds in delivery, max: {}", delivery.getItems().size(), MAX_FEEDS_IN_DELIVERY);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Error.TOO_MANY_ITEMS.asEntity(MAX_FEEDS_IN_DELIVERY))
                .build();
        }

        // fetch unread articles
        Adaptor adaptor = AdaptorFactory.getInstance(user);
        Map<String, List<FeedEntry>> unread =
            adaptor.getUnread(delivery.getItems().stream().map(item -> item.getFeedId()).collect(Collectors.toList()));
        LOG.debug("Fetched {} unread articles for {} feeds", 
            unread.values().stream().flatMap(List::stream).collect(Collectors.toList()), unread.size());
        
        // check if articles number is not above the limit
        int allArticles = unread.values().stream()
            .mapToInt(Collection::size)
            .sum();

        if (allArticles > MAX_ARTICLES_IN_DELIVERY) {
            LOG.warn("More than {} articles found", MAX_ARTICLES_IN_DELIVERY);
            unread = FeedUtils.getNewest(unread, MAX_ARTICLES_IN_DELIVERY);
        }

        // check if we got any articles
        boolean found = unread.values().stream().filter((list) -> !list.isEmpty()).count() > 0;

        // if no articles found and manual delivery, return error
        if (delivery.getManual() && !found) {
            LOG.error("No articles found for manual delivery. returning");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Error.NO_ARTICLES.asEntity())
                .build();
        }

        // if no articles and not manual, store error info
        if (!found) {
            LOG.error("No articles found for automatic delivery, storing information");
            Delivery toInsert = Delivery.builder()
                .manual(false)
                .error("NO ARTICLES")
                .items(delivery.getItems())
                .build();
            deliveryDAO.createDelivery(toInsert, userId);

            if (user.getNotifyNoArticles()) {
                LOG.debug("Notifications on no articles enabled, sending email to {}", user.getEmail());
                VelesRequest velesRequest = VelesRequest.builder()
                    .sender("contact@keendly.com")
                    .senderName("Keendly Support")
                    .subject("There was nothing to deliver this time")
                    .recipient(user.getEmail())
                    .message(noArticlesEmailMessage(delivery))
                    .build();
                velesService.sendEmail(velesRequest);
            }
            return Response.ok(toInsert).build();
        }

        Long deliveryId = deliveryDAO.createDelivery(delivery, userId);

        List<DeliveryItem> deliveryItems = Mapper.toDeliveryItems(delivery, unread);
        S3Object s3Items = storeItems(deliveryItems);
        DeliveryRequest request = Mapper.toDeliveryRequest(delivery, s3Items, deliveryId, user, false);

        String executionArn = startStateMachine(request);
        deliveryDAO.setExecutionArn(deliveryId, executionArn);

        return Response.status(Response.Status.CREATED)
            .entity(Delivery.builder()
                .id(deliveryId)
                .build())
            .build();
    }

    private S3Object storeItems(List<DeliveryItem> items) {
        String key = "messages/" + UUID.randomUUID().toString().replace("-", "") + ".json";
        amazonS3Client.putObject(BUCKET, key,
            new ByteArrayInputStream(
                Jackson.toJsonString(items).getBytes()), new ObjectMetadata());
        LOG.debug("Items stored in s3 with key: {}", key);

        return S3Object.builder()
            .bucket(BUCKET)
            .key(key)
            .build();
    }

    private String startStateMachine(DeliveryRequest request) {
        StartExecutionRequest startExecutionRequest = new StartExecutionRequest();
        startExecutionRequest.setInput(Jackson.toJsonString(request));
        startExecutionRequest.setStateMachineArn(STATE_MACHINE_ARN);
        StartExecutionResult result = awsStepFunctionsClient.startExecution(startExecutionRequest);
        LOG.debug("Started step functions execution: {}", result.getExecutionArn());
        return result.getExecutionArn();
    }

    private static String noArticlesEmailMessage(Delivery delivery) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ul>");
        for (DeliveryItem item : delivery.getItems()){
            sb.append("<li>");
            sb.append(item.getTitle());
            sb.append("</li>");
        }
        sb.append("</ul>");
        return VelesService.TEMPLATE.replace("{{FEEDS}}", sb.toString());
    }
}

