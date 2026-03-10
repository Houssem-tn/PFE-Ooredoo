package com.pfe.Dto;

import java.time.Instant;

public class AdminNotificationDto {

    private String type;          // ex: USER_REGISTERED
    private String title;         // ex: New user registration pending approval
    private String message;       // ex: John Doe has registered and is waiting for approval
    private String userId;
    private String fullName;
    private String email;
    private String status;        // PENDING
    private Instant createdAt;

    public AdminNotificationDto() {
    }

    public AdminNotificationDto(String type, String title, String message,
                                String userId, String fullName, String email,
                                String status, Instant createdAt) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
