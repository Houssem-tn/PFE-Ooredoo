package com.pfe.Entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "file_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    private String id;

    @Indexed
    private String bucket;

    @Indexed(unique = true)
    private String objectKey;

    private String originalFilename;
    private String contentType;
    private long size;

    @Indexed
    private FileEntityType entityType;

    @Indexed
    private String entityId;

    @Indexed
    private FileRole fileRole;

    private String uploadedBy;
    private Double locationLatitude;
    private Double locationLongitude;
    private Instant locationCapturedAt;
    private Double locationAccuracyMeters;
    private String captureSource;

    @Builder.Default
    private boolean deleted = false;

    @CreatedDate
    private Instant createdAt;
}
