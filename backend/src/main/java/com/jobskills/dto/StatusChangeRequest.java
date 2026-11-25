package com.jobskills.dto;

import lombok.Data;

/** An employer's status change, with an optional note stored in the history. */
@Data
public class StatusChangeRequest {
    private String status;
    private String message;
}
