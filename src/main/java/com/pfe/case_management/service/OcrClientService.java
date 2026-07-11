package com.pfe.case_management.service;


import com.pfe.case_management.dto.OcrResponse;
import com.pfe.config.RequestCorrelationFilter;
import com.pfe.metrics.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.io.File;

@Service
@RequiredArgsConstructor
public class OcrClientService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final BusinessMetrics businessMetrics;

    @Value("${ocr.api.url}")
    private String ocrApiUrl;

    public OcrResponse extract(File imageFile) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // Propagates the same correlation ID RequestCorrelationFilter put
        // in MDC for the inbound request that triggered this call — lets
        // an operator follow one upload through nginx -> backend -> OCR
        // in Loki using a single requestId (see OBSERVABILITY_BACKUP.md
        // "Correlation IDs"). Absent outside a request thread (e.g. a
        // background job calling OCR directly) — harmless, OCR's own
        // RequestIdMiddleware mints its own ID when the header is missing.
        String requestId = MDC.get(RequestCorrelationFilter.MDC_KEY);
        if (requestId != null) {
            headers.set(RequestCorrelationFilter.HEADER_NAME, requestId);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(imageFile));

        HttpEntity<MultiValueMap<String, Object>> request =
                new HttpEntity<>(body, headers);

        long started = System.nanoTime();
        boolean success = false;
        try {
            ResponseEntity<OcrResponse> response = restTemplate.postForEntity(
                    ocrApiUrl,
                    request,
                    OcrResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("OCR extraction failed");
            }

            success = true;
            return response.getBody();
        } finally {
            businessMetrics.recordOcrRequest(System.nanoTime() - started, success);
        }
    }
}