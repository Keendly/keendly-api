package com.keendly.adaptor.model;

import lombok.Data;

import java.util.List;

@Data
public class ExternalFeed {

    private String title;
    private String feedId;
    private List<String> categories;
}
