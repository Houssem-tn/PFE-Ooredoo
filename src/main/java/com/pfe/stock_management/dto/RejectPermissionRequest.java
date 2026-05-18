package com.pfe.stock_management.dto;

import jakarta.validation.constraints.NotBlank;

public class RejectPermissionRequest {

    @NotBlank(message = "Rejection reason is required")
    private String reviewComment;

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
}
