package com.pfe.case_management.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.HttpClientErrorException;
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
        } catch (HttpClientErrorException e) {
            // The OCR service already rejects invalid input (unsupported
            // extension, empty/corrupted image, etc.) with its own 4xx +
            // a real reason in the FastAPI HTTPException `detail` field —
            // RestTemplate throws before reaching the manual status check
            // above, so without this catch the real reason was discarded
            // and every rejection surfaced to the actual API caller as an
            // opaque 500 "An unexpected error occurred" instead of a 400
            // naming the actual problem. Found via a real k3d deployment
            // (Phase 4): a non-image file and an empty file both got a
            // clean 400 from OCR's own access log, but the client saw 500.
            throw new IllegalArgumentException(extractOcrRejectionReason(e));
        } finally {
            businessMetrics.recordOcrRequest(System.nanoTime() - started, success);
        }
    }

    private String extractOcrRejectionReason(HttpClientErrorException e) {
        try {
            JsonNode node = new ObjectMapper().readTree(e.getResponseBodyAsString());
            String detail = node.path("detail").asText(null);
            if (detail != null && !detail.isBlank()) {
                return detail;
            }
        } catch (Exception ignored) {
            // Fall through to the generic message below — this is a
            // best-effort enrichment, not something that itself should be
            // able to fail the request.
        }
        return "OCR rejected the file";
    }
}