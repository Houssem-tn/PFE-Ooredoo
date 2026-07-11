package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Entity.FileEntityType;
import com.pfe.MainPackage.Entity.FileMetadata;
import com.pfe.MainPackage.Entity.FileRole;
import com.pfe.MainPackage.Repository.FileMetadataRepository;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests upload validation and the MinIO-failure -> local-disk fallback path.
 * The real MinIO server is never contacted: MinioClient is a Mockito mock.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private MinioClient minioClient;
    @Mock
    private FileMetadataRepository fileMetadataRepository;

    private FileStorageService service;
    private Path tempStorageDir;

    @BeforeEach
    void setUp() throws IOException {
        service = new FileStorageService(minioClient, fileMetadataRepository);
        tempStorageDir = Files.createTempDirectory("pfe-file-storage-test");
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "localStorageDir", tempStorageDir.toString());
    }

    @Test
    void upload_shouldRejectNonImageContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> service.upload(file, FileEntityType.CASE, "case-1", FileRole.ATTACHMENT, "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only image files are allowed");
    }

    @Test
    void upload_shouldRejectOversizedImage() {
        byte[] tooBig = new byte[9 * 1024 * 1024]; // > 8MB limit
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooBig);

        assertThatThrownBy(() -> service.upload(file, FileEntityType.CASE, "case-1", FileRole.ATTACHMENT, "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be <= 8 MB");
    }

    @Test
    void upload_shouldRejectWhenOnlyOneOfLatitudeLongitudeProvided() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "img".getBytes());

        assertThatThrownBy(() -> service.upload(
                file, FileEntityType.CASE, "case-1", FileRole.BEFORE_IMAGE, "user@example.com",
                36.8, null, null, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude and longitude must be provided together");
    }

    @Test
    void upload_shouldFallBackToLocalDiskWhenMinioIsUnavailable() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "img-bytes".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("connection refused"));
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        FileMetadata metadata = service.upload(file, FileEntityType.CASE, "case-1", FileRole.ATTACHMENT, "user@example.com");

        assertThat(metadata.getBucket()).isEqualTo("LOCAL_FS");
        assertThat(Files.exists(tempStorageDir.resolve(metadata.getObjectKey()))).isTrue();
    }
}
