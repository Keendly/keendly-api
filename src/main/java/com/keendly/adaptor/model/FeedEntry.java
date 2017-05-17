package com.keendly.adaptor.model;

import lombok.Data;

import java.util.Date;

@Data
public class FeedEntry {

    private String id;
    private String url;
    private String title;
    private String author;
    private Date published;
    private String content;
}

