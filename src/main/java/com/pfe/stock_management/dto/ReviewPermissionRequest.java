package com.pfe.stock_management.dto;

import java.util.List;

public class ReviewPermissionRequest {

    private double approvedCableMeters;
    private List<ApprovedItemRequest> approvedItems;
    private String reviewComment;

    public double getApprovedCableMeters() { return approvedCableMeters; }
    public void setApprovedCableMeters(double approvedCableMeters) { this.approvedCableMeters = approvedCableMeters; }

    public List<ApprovedItemRequest> getApprovedItems() { return approvedItems; }
    public void setApprovedItems(List<ApprovedItemRequest> approvedItems) { this.approvedItems = approvedItems; }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

    public static class ApprovedItemRequest {
        private String itemId;
        private double approvedQuantity;

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public double getApprovedQuantity() { return approvedQuantity; }
        public void setApprovedQuantity(double approvedQuantity) { this.approvedQuantity = approvedQuantity; }
    }
}
