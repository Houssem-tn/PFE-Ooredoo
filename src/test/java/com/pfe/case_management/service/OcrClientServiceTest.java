package com.pfe.case_management.service;

import com.pfe.case_management.dto.OcrResponse;
import com.pfe.metrics.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The real OCR microservice is never contacted: RestTemplate is replaced
 * with a Mockito mock via reflection (OcrClientService hardcodes
 * `new RestTemplate()` as a field initializer with no constructor/setter).
 */
@ExtendWith(MockitoExtension.class)
class OcrClientServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private BusinessMetrics businessMetrics;

    private OcrClientService service;

    @BeforeEach
    void setUp() {
        service = new OcrClientService(businessMetrics);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "ocrApiUrl", "http://ocr.invalid/extract");
    }

    @Test
    void extract_shouldReturnParsedResponseOnSuccess() {
        OcrResponse body = new OcrResponse();
        body.setSuccess(true);
        body.setDocumentType("CIN");

        when(restTemplate.postForEntity(eq("http://ocr.invalid/extract"), any(), eq(OcrResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        OcrResponse result = service.extract(new File("dummy.png"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDocumentType()).isEqualTo("CIN");
    }

    @Test
    void extract_shouldThrowWhenResponseBodyIsNull() {
        when(restTemplate.postForEntity(eq("http://ocr.invalid/extract"), any(), eq(OcrResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertThatThrownBy(() -> service.extract(new File("dummy.png")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OCR extraction failed");
    }

    @Test
    void extract_shouldThrowWhenResponseStatusIsNotSuccessful() {
        when(restTemplate.postForEntity(eq("http://ocr.invalid/extract"), any(), eq(OcrResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new OcrResponse()));

        assertThatThrownBy(() -> service.extract(new File("dummy.png")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OCR extraction failed");
    }

    @Test
    void extract_shouldPropagateWhenOcrServiceIsUnreachable() {
        when(restTemplate.postForEntity(eq("http://ocr.invalid/extract"), any(), eq(OcrResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service.extract(new File("dummy.png")))
                .isInstanceOf(ResourceAccessException.class);
    }
}
