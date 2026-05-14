package com.pfe.case_management.controller;


import com.pfe.case_management.dto.OcrResponse;
import com.pfe.case_management.service.OcrClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
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
            File tempFile = File.createTempFile("ocr-", "-" + file.getOriginalFilename());
            file.transferTo(tempFile);

            try {
                OcrResponse response = ocrClientService.extract(tempFile);
                results.add(response);
            } finally {
                tempFile.delete();
            }
        }

        return results;
    }
}