package com.pfe.Service;

import com.pfe.Entity.FileEntityType;
import com.pfe.Entity.FileMetadata;
import com.pfe.Entity.FileRole;
import com.pfe.Repository.FileMetadataRepository;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@Service
public class FileStorageService {

    private final MinioClient minioClient;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.public-base-url}")
    private String publicBaseUrl;

    public FileStorageService(MinioClient minioClient, FileMetadataRepository fileMetadataRepository) {
        this.minioClient = minioClient;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    public FileMetadata upload(
            MultipartFile file,
            FileEntityType entityType,
            String entityId,
            FileRole fileRole,
            String uploadedBy
    ) {
        validateImage(file);

        try {
            ensureBucketExists();

            String safeOriginalName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename().replaceAll("\\s+", "_")
                    : "file";

            String objectKey = entityType.name().toLowerCase()
                    + "/" + entityId
                    + "/" + fileRole.name().toLowerCase()
                    + "/" + UUID.randomUUID()
                    + "-" + safeOriginalName;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            FileMetadata metadata = FileMetadata.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .entityType(entityType)
                    .entityId(entityId)
                    .fileRole(fileRole)
                    .uploadedBy(uploadedBy)
                    .createdAt(Instant.now())
                    .build();

            return fileMetadataRepository.save(metadata);

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload file to MinIO",
                    e
            );
        }
    }

    public StoredFile loadFile(String fileId) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "File not found: " + fileId
                ));

        try {
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(metadata.getBucket())
                            .object(metadata.getObjectKey())
                            .build()
            );

            return new StoredFile(metadata, inputStream);

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read file from MinIO. fileId=" + fileId
                            + ", bucket=" + metadata.getBucket()
                            + ", objectKey=" + metadata.getObjectKey(),
                    e
            );
        }
    }

    public FileMetadata getMetadata(String fileId) {
        return fileMetadataRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "File not found: " + fileId
                ));
    }

    public void softDelete(String fileId) {
        FileMetadata metadata = getMetadata(fileId);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(metadata.getBucket())
                            .object(metadata.getObjectKey())
                            .build()
            );

            metadata.setDeleted(true);
            fileMetadataRepository.save(metadata);

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete file from MinIO",
                    e
            );
        }
    }

    public String buildViewUrl(String fileId) {
        return publicBaseUrl + "/" + fileId + "/view";
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build()
            );
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }

        long maxSize = 5L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Image must be <= 5 MB");
        }
    }

    public record StoredFile(FileMetadata metadata, InputStream inputStream) {}
}