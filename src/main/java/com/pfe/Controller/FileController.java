package com.pfe.Controller;

import com.pfe.Service.FileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{fileId}/view")
    public ResponseEntity<InputStreamResource> viewFile(@PathVariable String fileId) {
        FileStorageService.StoredFile storedFile = fileStorageService.loadFile(fileId);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String contentType = storedFile.metadata().getContentType();
        if (contentType != null && !contentType.isBlank()) {
            mediaType = MediaType.parseMediaType(contentType);
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(storedFile.metadata().getSize())
                .cacheControl(CacheControl.noCache())
                .body(new InputStreamResource(storedFile.inputStream()));
    }

    @GetMapping("/{fileId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String fileId) {
        FileStorageService.StoredFile storedFile = fileStorageService.loadFile(fileId);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String contentType = storedFile.metadata().getContentType();
        if (contentType != null && !contentType.isBlank()) {
            mediaType = MediaType.parseMediaType(contentType);
        }

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(storedFile.metadata().getOriginalFilename())
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(storedFile.metadata().getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(new InputStreamResource(storedFile.inputStream()));
    }
}