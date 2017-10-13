package com.keendly.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class AuthorizerHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    public static final String KEY = System.getenv("AUTH_KEY");

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizerHandler.class);

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        LOG.debug(AuthorizerHandler.class.getSimpleName() + " invoked");
        String resource = (String) event.get("methodArn");
        String token = (String) event.get("authorizationToken");
        try {
            if (token == null){
                return generatePolicy("-1", "Deny", resource);
            }
            LOG.info("Received token " + token);
            String userId = getUserId(token);
            return generatePolicy(userId, "Allow", resource);
        } catch (Exception e) {
            LOG.warn("Exception authenticating", e);
            return generatePolicy("-1", "Deny", resource);
        }
    }

    private Map<String, Object> generatePolicy(String principalId, String effect, String resource) {
        Map<String, Object> authResponse = new HashMap();
        authResponse.put("principalId", principalId);
        Map<String, Object> policyDocument = new HashMap();
        policyDocument.put("Version", "2012-10-17"); // default version
        Map<String, String> statementOne = new HashMap();
        statementOne.put("Action", "execute-api:Invoke"); // default action
        statementOne.put("Effect", effect);
        statementOne.put("Resource", "arn:aws:execute-api:*:*:*");
        policyDocument.put("Statement", new Object[] {statementOne});
        authResponse.put("policyDocument", policyDocument);
//        if ("Allow".equals(effect)) {
//            Map<String, Object> context = new HashMap();
//            context.put("key", "value");
//            context.put("numKey", Long.valueOf(1L));
//            context.put("boolKey", Boolean.TRUE);
//            authResponse.put("context", context);
//        }
        return authResponse;
    }

    public String getUserId(String token) {
        LOG.info("signing key: " + KEY);

        Claims claims = Jwts.parser().setSigningKey(KEY).parseClaimsJws(token).getBody();
        return claims.get("userId", String.class);
    }
}
