package com.keendly.api;

import com.keendly.auth.AuthorizerHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.DefaultClaims;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Path("/login")
public class LoginResource {

    private static int ONE_MONTH = 60 * 60 * 24 * 30;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response login(
        @QueryParam("code") String code,
        @QueryParam("state") String state,
        @QueryParam("error") String error) {

        Map<String, String> ret = new HashMap<String, String>();
//        ret.put("accessToken", authToken);
        ret.put("accessToken", generate(Long.valueOf(code), 6000)); // TODO for testing
        ret.put("tokeType", "Bearer");
        ret.put("expiresIn", Integer.toString(6000));
//        ret.put("expiresIn", Integer.toString(expiresIn));
        ret.put("scope", "write");

        return Response.ok(ret).build();
    }

    private String generate(long userId, int expiresIn) {
        Claims claims = new DefaultClaims();
        claims.put("userId", Long.toString(userId));
        claims.setExpiration(Date.from(LocalDateTime.now().plusSeconds(ONE_MONTH).
            atZone(ZoneId.systemDefault()).toInstant()));

        return Jwts.builder()
            .setClaims(claims)
            .signWith(SignatureAlgorithm.HS512, AuthorizerHandler.KEY)
            .compact();
    }
}
