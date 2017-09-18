package com.keendly.api;

import java.util.HashMap;
import java.util.Map;

public enum Error {

    DELIVERY_EMAIL_NOT_CONFIGURED("Send-to-Kindle email address not configured"),
    TOO_MANY_ITEMS("Max number of feeds in single delivery: %d"),
    WRONG_EMAIL("Email address incorrect, allowed domains: %s"),
    NO_ARTICLES("No unread articles found"),
    TOO_MANY_SUBSCRIPTIONS("Max number of scheduled deliveries: %d");

    private String message;

    Error(String message){
        this.message = message;
    }

    public Object asEntity(Object... msgParams) {
        Map<String, String> map = new HashMap<>();
        map.put("code", name());
        map.put("description", String.format(message, msgParams));

        return map;
    }
}
