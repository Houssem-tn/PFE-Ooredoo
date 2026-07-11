package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Entity.FileEntityType;
import com.pfe.MainPackage.Entity.FileMetadata;
import com.pfe.MainPackage.Entity.FileRole;
import com.pfe.MainPackage.Repository.FileMetadataRepository;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final String LOCAL_BUCKET = "LOCAL_FS";

    // File preview/download URLs are returned as origin-relative paths so the
    // browser resolves them against whatever origin served the page (localhost,
    // LAN, ngrok, ...). This avoids absolute URLs that leak a wrong port (e.g.
    // ":80" on https) and prevents CORS / mixed-content issues.
    private static final String FILES_BASE_PATH = "/api/v1/files";

    private final MinioClient minioClient;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${app.storage.local-dir:${java.io.tmpdir}/pfe-files}")
    private String localStorageDir;

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
        return upload(file, entityType, entityId, fileRole, uploadedBy, null, null, null, null, null);
    }

    public FileMetadata upload(
            MultipartFile file,
            FileEntityType entityType,
            String entityId,
            FileRole fileRole,
            String uploadedBy,
            Double locationLatitude,
            Double locationLongitude,
            Instant locationCapturedAt,
            Double locationAccuracyMeters,
            String captureSource
    ) {
        validateImage(file);
        validateCoordinates(locationLatitude, locationLongitude);

        String safeOriginalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("\\s+", "_")
                : "file";

        String objectKey = buildObjectKey(entityType, entityId, fileRole, safeOriginalName);

        try {
            ensureBucketExists();
            storeInMinio(file, objectKey);
            return persistMetadata(
                    bucket,
                    objectKey,
                    file,
                    entityType,
                    entityId,
                    fileRole,
                    uploadedBy,
                    locationLatitude,
                    locationLongitude,
                    locationCapturedAt,
                    locationAccuracyMeters,
                    captureSource
            );

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage(),
                    e
            );
        } catch (Exception e) {
            log.warn(
                    "MinIO upload failed for entityType={}, entityId={}, fileRole={}, objectKey={}. Falling back to local storage.",
                    entityType,
                    entityId,
                    fileRole,
                    objectKey,
                    e
            );

            try {
                storeLocally(file, objectKey);
                return persistMetadata(
                        LOCAL_BUCKET,
                        objectKey,
                        file,
                        entityType,
                        entityId,
                        fileRole,
                        uploadedBy,
                        locationLatitude,
                        locationLongitude,
                        locationCapturedAt,
                        locationAccuracyMeters,
                        captureSource
                );
            } catch (IOException localStorageException) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to store uploaded file",
                        localStorageException
                );
            }
        }
    }

    public StoredFile loadFile(String fileId) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "File not found: " + fileId
                ));

        try {
            if (isLocalStorage(metadata)) {
                return new StoredFile(metadata, Files.newInputStream(resolveLocalPath(metadata.getObjectKey())));
            }

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
            if (isLocalStorage(metadata)) {
                Files.deleteIfExists(resolveLocalPath(metadata.getObjectKey()));
                metadata.setDeleted(true);
                fileMetadataRepository.save(metadata);
                return;
            }

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
        return FILES_BASE_PATH + "/" + fileId + "/view";
    }

    public String buildDownloadUrl(String fileId) {
        return FILES_BASE_PATH + "/" + fileId + "/download";
    }

    public List<FileMetadata> listByEntityAndRole(
            FileEntityType entityType,
            String entityId,
            FileRole fileRole
    ) {
        return fileMetadataRepository.findByEntityTypeAndEntityIdAndFileRoleAndDeletedFalseOrderByCreatedAtDesc(
                entityType,
                entityId,
                fileRole
        );
    }

    private String buildObjectKey(
            FileEntityType entityType,
            String entityId,
            FileRole fileRole,
            String safeOriginalName
    ) {
        return entityType.name().toLowerCase()
                + "/" + entityId
                + "/" + fileRole.name().toLowerCase()
                + "/" + UUID.randomUUID()
                + "-" + safeOriginalName;
    }

    private void storeInMinio(MultipartFile file, String objectKey) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
    }

    private void storeLocally(MultipartFile file, String objectKey) throws IOException {
        Path targetPath = resolveLocalPath(objectKey);
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private FileMetadata persistMetadata(
            String bucketName,
            String objectKey,
            MultipartFile file,
            FileEntityType entityType,
            String entityId,
            FileRole fileRole,
            String uploadedBy,
            Double locationLatitude,
            Double locationLongitude,
            Instant locationCapturedAt,
            Double locationAccuracyMeters,
            String captureSource
    ) {
        FileMetadata metadata = FileMetadata.builder()
                .bucket(bucketName)
                .objectKey(objectKey)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .entityType(entityType)
                .entityId(entityId)
                .fileRole(fileRole)
                .uploadedBy(uploadedBy)
                .locationLatitude(locationLatitude)
                .locationLongitude(locationLongitude)
                .locationCapturedAt(locationCapturedAt)
                .locationAccuracyMeters(locationAccuracyMeters)
                .captureSource(normalizeCaptureSource(captureSource))
                .createdAt(Instant.now())
                .build();

        return fileMetadataRepository.save(metadata);
    }

    private boolean isLocalStorage(FileMetadata metadata) {
        return metadata != null && LOCAL_BUCKET.equals(metadata.getBucket());
    }

    private Path resolveLocalPath(String objectKey) {
        Path basePath = Paths.get(localStorageDir).toAbsolutePath().normalize();
        Path resolvedPath = basePath.resolve(objectKey.replace("/", java.io.File.separator)).normalize();

        if (!resolvedPath.startsWith(basePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        return resolvedPath;
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

        long maxSize = 8L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Image must be <= 8 MB");
        }
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return;
        }

        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together");
        }

        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }

        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    private String normalizeCaptureSource(String captureSource) {
        if (captureSource == null || captureSource.isBlank()) {
            return null;
        }

        String normalized = captureSource.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("camera") && !normalized.equals("gallery")) {
            return "unknown";
        }

        return normalized;
    }

    public record StoredFile(FileMetadata metadata, InputStream inputStream) {}
}
