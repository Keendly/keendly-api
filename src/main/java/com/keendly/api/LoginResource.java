package com.keendly.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.keendly.adaptor.Adaptor;
import com.keendly.adaptor.AdaptorFactory;
import com.keendly.adaptor.model.ExternalUser;
import com.keendly.adaptor.model.auth.Credentials;
import com.keendly.auth.AuthorizerHandler;
import com.keendly.dao.ClientDao;
import com.keendly.dao.UserDao;
import com.keendly.model.Provider;
import com.keendly.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.DefaultClaims;
import lombok.Builder;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

@Path("/login")
public class LoginResource {

    private static final Logger LOG = LoggerFactory.getLogger(LoginResource.class);
    private static int ONE_HOUR = 60 * 60;
    private static int ONE_MONTH = ONE_HOUR * 24 * 30;

    private UserDao userDAO = new UserDao();
    private ClientDao clientDAO = new ClientDao();

    private enum GrantType {
        BEARER("bearer", ONE_HOUR), // to get token on behalf of a user
        PASSWORD("password", ONE_MONTH), // authenticate with password
        AUTHENTICATION_CODE("authentication_code", ONE_MONTH), // authenticate with code - OAuth
        CLIENT_CREDENTIALS("client_credentials", ONE_HOUR); // for clients, to get token without user's scope

        String text;
        int expiresIn;
        GrantType(String s, int expiresIn){
            this.expiresIn = expiresIn;
            this.text = s;
        }

        static GrantType fromString(String s){
            for (GrantType grantType : GrantType.values()){
                if (grantType.text.equals(s)){
                    return grantType;
                }
            }
            return null;
        }
    }

    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response login(LoginRequest request) {
        GrantType grantType = GrantType.fromString(request.getGrantType());
        switch (grantType) {
        case PASSWORD:
        case AUTHENTICATION_CODE:
            if (request.getCode() != null) {
                // state required with authorization code
                if (!validateStateToken(request.getState(), request.getProvider())) {
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
            }
            Credentials credentials = Credentials.builder()
                .authorizationCode(request.getCode())
                .username(request.getUsername())
                .password(request.getPassword())
                .build();
            Adaptor adaptor = AdaptorFactory.getInstance(request.getProvider(), credentials);
            ExternalUser externalUser = adaptor.getUser();
            Optional<User> user = userDAO.findByProviderId(externalUser.getId(), request.getProvider());
            String token;
            if (user.isPresent()) {
                token = generate(user.get().getId(), grantType.expiresIn);
                userDAO.updateTokens(user.get().getId(), adaptor.getToken());
            } else {
                long userId =
                    userDAO.createUser(externalUser.getId(), externalUser.getUserName(), request.getProvider());
                token = generate(userId, grantType.expiresIn);
                userDAO.updateTokens(userId, adaptor.getToken());
            }
            return Response.ok(token).build();
        case BEARER:
            Optional<String> clientSecret = clientDAO.findClientSecret(request.getClientId());
            if (!clientSecret.isPresent()) {
                LOG.error("Couldn't find client with id: {}", request.getClientId());
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            Optional<Integer> userId = decode(request.getToken(), clientSecret.get());
            if (!userId.isPresent()) {
                LOG.error("Couldn't find userId in token {}", request.getToken());
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            String authToken = generate(Long.valueOf(userId.get()), grantType.expiresIn);
            return Response.ok(authToken).build();
        case CLIENT_CREDENTIALS:
            Optional<String> secret = clientDAO.findClientSecret(request.getClientId());
            if (!secret.isPresent() || !secret.get().equals(request.getClientSecret())) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            } else {
                return Response.ok(generate(-1L, grantType.expiresIn)).build();
            }
        default:
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    private static String generate(long userId, int expiresIn) {
        Claims claims = new DefaultClaims();
        claims.put("userId", Long.toString(userId));
        claims.setExpiration(Date.from(LocalDateTime.now().plusSeconds(expiresIn).
            atZone(ZoneId.systemDefault()).toInstant()));

        return Jwts.builder()
            .setClaims(claims)
            .signWith(SignatureAlgorithm.HS512, AuthorizerHandler.KEY)
            .compact();
    }

    private static Optional<Integer> decode(String token, String secret){
        try {
            Claims claims = Jwts.parser()
                .setSigningKey(secret.getBytes())
                .parseClaimsJws(token)
                .getBody();
            return Optional.of(claims.get("userId", Integer.class));
        } catch (Exception e){
            LOG.error("Error decoding token", e);
            return Optional.empty();
        }
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getState(@QueryParam("provider") Provider provider) {
        String state = generateStateToken(provider);
        return Response.ok(Collections.singletonMap("state", state)).build();
    }

    private static String generateStateToken(Provider p) {
        Claims claims = new DefaultClaims();
        claims.put("provider", p.name());
        claims.setExpiration(Date.from(LocalDateTime.now().plusMinutes(5).
            atZone(ZoneId.systemDefault()).toInstant()));

        String token = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS256, AuthorizerHandler.KEY).compact();
        return URLEncoder.encode(token);
    }

    private static boolean validateStateToken(String encodedToken, Provider provider) {
        try {
            String token = URLDecoder.decode(encodedToken);
            Claims claims = Jwts.parser().setSigningKey(AuthorizerHandler.KEY).parseClaimsJws(token).getBody();

            Provider p = Provider.valueOf(claims.get("provider", String.class));
            if (p != provider) {
                LOG.error("Incorrect provider in token {}, expected {}, got {}", encodedToken, provider.name(),
                    p.name());
                return false;
            }

            return true;
        } catch (Exception e) {
            LOG.error("Error validating state token", e);
            return false;
        }
    }

    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class LoginRequest {

        private Provider provider;
        private String code;
        private String username;
        private String password;
        @JsonProperty("grant_type")
        private String grantType;
        @JsonProperty("client_id")
        private String clientId;
        @JsonProperty("client_secret")
        private String clientSecret;
        private String token;
        private String state;
    }
}
