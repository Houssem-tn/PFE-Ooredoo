package com.pfe.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyEnvironmentPostProcessorTest {

    private final ProductionSafetyEnvironmentPostProcessor postProcessor =
            new ProductionSafetyEnvironmentPostProcessor();

    @Test
    void postProcessEnvironment_shouldSkipValidationWhenProdProfileIsNotActive() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        // Deliberately no config set at all — must not throw outside prod.

        postProcessor.postProcessEnvironment(env, null);
        // no exception -> pass
    }

    @Test
    void postProcessEnvironment_shouldRejectCompletelyEmptyProdConfig() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MONGODB_URI is not set")
                .hasMessageContaining("APP_JWT_SECRET is not set")
                .hasMessageContaining("MINIO_URL is not set")
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS is not set")
                .hasMessageContaining("APP_FRONTEND_URL is not set")
                .hasMessageContaining("OCR_API_URL is not set");
    }

    @Test
    void postProcessEnvironment_shouldRejectDevPlaceholderJwtSecretInProd() {
        MockEnvironment env = validBaseEnvironment();
        env.setProperty("app.jwt.secret", "dev-secret-change-me-dev-secret-change-me");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, null))
                .hasMessageContaining("still the development placeholder");
    }

    @Test
    void postProcessEnvironment_shouldRejectShortJwtSecret() {
        MockEnvironment env = validBaseEnvironment();
        env.setProperty("app.jwt.secret", "too-short");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, null))
                .hasMessageContaining("too short");
    }

    @Test
    void postProcessEnvironment_shouldRejectDefaultMinioCredentials() {
        MockEnvironment env = validBaseEnvironment();
        env.setProperty("minio.access-key", "minioadmin");
        env.setProperty("minio.secret-key", "minioadmin");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, null))
                .hasMessageContaining("still the default dev credentials");
    }

    @Test
    void postProcessEnvironment_shouldRejectLocalhostMongoUriInProd() {
        MockEnvironment env = validBaseEnvironment();
        env.setProperty("spring.mongodb.uri", "mongodb://localhost:27017/pfe");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, null))
                .hasMessageContaining("MONGODB_URI points at localhost");
    }

    @Test
    void postProcessEnvironment_shouldRejectLocalhostCorsOriginInProd() {
        MockEnvironment env = validBaseEnvironment();
        env.setProperty("app.cors.allowed-origins", "https://app.example.com,http://localhost:4200");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, null))
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS contains a localhost");
    }

    @Test
    void postProcessEnvironment_shouldRequireMailCredentialsOnlyWhenMailIsEnabled() {
        MockEnvironment env = validBaseEnvironment();
        env.setProperty("app.mail.enabled", "true");
        // no spring.mail.username/password set

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, null))
                .hasMessageContaining("MAIL_ENABLED is true but MAIL_USERNAME/MAIL_PASSWORD");
    }

    @Test
    void postProcessEnvironment_shouldNotRequireMailCredentialsWhenMailIsDisabled() {
        MockEnvironment env = validBaseEnvironment();
        env.setProperty("app.mail.enabled", "false");

        postProcessor.postProcessEnvironment(env, null); // must not throw
    }

    @Test
    void postProcessEnvironment_shouldPassWithFullyValidProdConfig() {
        MockEnvironment env = validBaseEnvironment();

        postProcessor.postProcessEnvironment(env, null); // must not throw
        assertThat(env.getActiveProfiles()).containsExactly("prod");
    }

    private MockEnvironment validBaseEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.mongodb.uri", "mongodb://prod-mongo.internal:27017/pfe");
        env.setProperty("app.jwt.secret", "a-sufficiently-long-random-production-secret-value");
        env.setProperty("minio.url", "https://minio.internal");
        env.setProperty("minio.access-key", "real-access-key");
        env.setProperty("minio.secret-key", "real-secret-key");
        env.setProperty("app.cors.allowed-origins", "https://app.example.com");
        env.setProperty("app.mail.enabled", "false");
        env.setProperty("app.frontend-url", "https://app.example.com");
        env.setProperty("ocr.api.url", "https://ocr.internal/extract");
        return env;
    }
}
