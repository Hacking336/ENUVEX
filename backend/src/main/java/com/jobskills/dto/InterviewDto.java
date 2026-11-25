package com.jobskills.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** The current interview arrangement, or the last one before it was cancelled. */
@Data
public class InterviewDto {
    private Long id;
    private LocalDateTime interviewAt;
    private String interviewType;
    private String location;
    private String meetingLink;
    private String instructions;
    private String employerMessage;
    private String status;
    private LocalDateTime updatedAt;
}
