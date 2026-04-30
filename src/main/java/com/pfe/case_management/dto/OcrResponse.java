package com.pfe.case_management.dto;

import lombok.Data;
import java.util.Map;

@Data
public class OcrResponse {
    private boolean success;
    private String documentType;
    private Map<String, Object> data;
    private String rawText;
    private String ocrPass;
    private Double ocrConfidence;
}
