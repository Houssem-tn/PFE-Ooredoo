package com.pfe.case_management.controller;


import com.pfe.case_management.dto.OcrResponse;
import com.pfe.case_management.service.OcrClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final OcrClientService ocrClientService;

    @PostMapping(
            value = "/extract",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public List<OcrResponse> extractMultiple(
            @RequestPart("files") MultipartFile[] files
    ) throws Exception {

        List<OcrResponse> results = new ArrayList<>();

        for (MultipartFile file : files) {
            // The original filename is fully attacker-controlled and is only ever used
            // as a temp-file suffix (not a path) — strip it to a safe extension so it
            // can't inject path separators / ".." sequences into File.createTempFile.
            File tempFile = File.createTempFile("ocr-", safeSuffix(file.getOriginalFilename()));
            file.transferTo(tempFile);

            try {
                OcrResponse response = ocrClientService.extract(tempFile);
                results.add(response);
            } finally {
                if (!tempFile.delete()) {
                    log.warn("Failed to delete OCR temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }

        return results;
    }

    private static String safeSuffix(String originalFilename) {
        if (originalFilename == null) {
            return ".tmp";
        }
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot >= 0 ? originalFilename.substring(dot) : "";
        // Keep only a short, plain alphanumeric extension; anything else
        // (path separators, "..", unexpected characters) is dropped.
        extension = extension.replaceAll("[^a-zA-Z0-9.]", "");
        if (extension.length() > 10 || extension.isBlank()) {
            extension = ".tmp";
        }
        return extension;
    }
}
