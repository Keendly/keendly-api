package com.keendly.dao;

public enum DDL {

    CREATE_USER("CREATE TABLE keendlyuser ("
        + "id BIGINT NOT NULL, "
        + "created TIMESTAMP(6) WITH TIME ZONE, "
        + "last_modified TIMESTAMP(6) WITH TIME ZONE, "
        + "delivery_email CHARACTER VARYING(255), "
        + "email CHARACTER VARYING(255), "
        + "provider CHARACTER VARYING(255) NOT NULL, "
        + "provider_id CHARACTER VARYING(255) NOT NULL, "
        + "access_token CHARACTER VARYING(1000), "
        + "last_login TIMESTAMP(6) WITH TIME ZONE, "
        + "refresh_token CHARACTER VARYING(1000), "
        + "delivery_sender CHARACTER VARYING(255), "
        + "notify_no_articles BOOLEAN, "
        + "premium_subscription_id CHARACTER VARYING(100), "
        + "PRIMARY KEY (id), "
        + "CONSTRAINT uk_6vhj1cwggrr6dy1sxskrbt0y4 UNIQUE (provider, provider_id));"),

    CREATE_SUBSCRIPTION("CREATE TABLE subscription ("
        + "id BIGINT NOT NULL, "
        + "created TIMESTAMP(6) WITH TIME ZONE, "
        + "last_modified TIMESTAMP(6) WITH TIME ZONE, "
        + "active BOOLEAN NOT NULL, "
        + "frequency CHARACTER VARYING(255), "
        + "time CHARACTER VARYING(255) NOT NULL, "
        + "timezone CHARACTER VARYING(255) NOT NULL, "
        + "user_id BIGINT NOT NULL, "
        + "deleted BOOLEAN NOT NULL, "
        + "PRIMARY KEY (id), "
        + "CONSTRAINT fk_iwmhiweloa3dnrvfpdl47tf49 FOREIGN KEY (user_id) REFERENCES keendlyuser (id));"),

    CREATE_SUBSCRIPTION_ITEM("CREATE TABLE subscriptionitem ("
        + "id BIGINT NOT NULL, "
        + "feed_id CHARACTER VARYING(255) NOT NULL, "
        + "full_article BOOLEAN NOT NULL, "
        + "mark_as_read BOOLEAN NOT NULL, "
        + "with_images BOOLEAN NOT NULL, "
        + "subscription_id BIGINT NOT NULL, "
        + "created TIMESTAMP(6) WITH TIME ZONE, "
        + "last_modified TIMESTAMP(6) WITH TIME ZONE, "
        + "title CHARACTER VARYING(255) NOT NULL, "
        + "PRIMARY KEY (id), "
        + "CONSTRAINT fk_e0vvyunqtl0jgrg81aadg9eyc FOREIGN KEY (subscription_id) REFERENCES subscription (id));"),

    CREATE_DELIVERY("CREATE TABLE delivery ("
        + "id BIGINT NOT NULL, "
        + "created TIMESTAMP(6) WITH TIME ZONE, "
        + "last_modified TIMESTAMP(6) WITH TIME ZONE, "
        + "date TIMESTAMP(6) WITH TIME ZONE, "
        + "info CHARACTER VARYING(255), "
        + "manual BOOLEAN NOT NULL, "
        + "subscription_id BIGINT, "
        + "user_id BIGINT NOT NULL, "
        + "errordescription CHARACTER VARYING(255), "
        + "execution CHARACTER VARYING(255), "
        + "PRIMARY KEY (id), "
        + "CONSTRAINT fk_1ewuiqv0iqkeq3oxd9218hd9c FOREIGN KEY (subscription_id) REFERENCES subscription (id), "
        + "CONSTRAINT fk_dhf0kx7m5lcgx98n7qv9vn6c5 FOREIGN KEY (user_id) REFERENCES keendlyuser (id));"),

    CREATE_DELIVERY_ITEM("CREATE TABLE deliveryitem ("
        + "id BIGINT NOT NULL, "
        + "created TIMESTAMP(6) WITH TIME ZONE, "
        + "last_modified TIMESTAMP(6) WITH TIME ZONE, "
        + "feed_id CHARACTER VARYING(255) NOT NULL, "
        + "full_article BOOLEAN NOT NULL, "
        + "mark_as_read BOOLEAN NOT NULL, "
        + "title CHARACTER VARYING(255) NOT NULL, "
        + "with_images BOOLEAN NOT NULL, "
        + "delivery_id BIGINT NOT NULL, "
        + "PRIMARY KEY (id), "
        + "CONSTRAINT fk_afuxcyuvy7y7yecy4qnb0kqtr FOREIGN KEY (delivery_id) REFERENCES delivery (id));"),

    CREATE_SEQUENCE("CREATE SEQUENCE hibernate_sequence START 1;"),

    CREATE_CLIENT("CREATE TABLE client ("
        + "id BIGINT NOT NULL, "
        + "client_id CHARACTER VARYING(255) NOT NULL, "
        + "client_secret CHARACTER VARYING(255) NOT NULL, "
        + "name CHARACTER VARYING(255) NOT NULL, "
        + "PRIMARY KEY (id), "
        + "UNIQUE (client_id));"),

    CREATE_PUSH_SUBSCRIPTION("CREATE TABLE pushsubscription ("
        +" id BIGINT NOT NULL,"
        +" user_id BIGINT NOT NULL,"
        +" auth CHARACTER VARYING(255),"
        +" key CHARACTER VARYING(255),"
        +" endpoint CHARACTER VARYING(1024),"
        +" CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES keendlyuser (id)"
        +")");

    private String sql;

    DDL(String sql) {
        this.sql = sql;
    }

    public String sql(){
        return sql;
    }
}
