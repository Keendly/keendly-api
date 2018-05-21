package com.keendly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Date;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Premium {
    private boolean active;
    private Date expires;
    private boolean cancellable;
}
