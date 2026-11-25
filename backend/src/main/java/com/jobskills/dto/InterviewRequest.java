package com.jobskills.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** An employer's interview invitation or reschedule. */
@Data
public class InterviewRequest {
    /** Local date and time, e.g. 2026-12-02T14:30. */
    private LocalDateTime interviewAt;
    /** IN_PERSON, ONLINE or PHONE. */
    private String interviewType;
    private String location;
    private String meetingLink;
    private String instructions;
    private String employerMessage;
}
