package com.jobskills.dto;

/** A single recruitment insight rule. */
public class EmployerInsightDto {

    private String title;
    private String description;
    private String priority; // "info", "warning", "error"

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}