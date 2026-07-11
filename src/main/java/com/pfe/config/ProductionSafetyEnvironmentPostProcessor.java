package com.pfe.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fails startup with a clear, aggregated error when the `prod` profile is
 * active and critical configuration is missing or looks like a
 * dev/localhost default.
 *
 * Implemented as an {@link EnvironmentPostProcessor} (registered in
 * META-INF/spring.factories) rather than a regular {@code @Component} so it
 * runs during environment preparation, before the ApplicationContext exists
 * — i.e. strictly before any bean (including the Mongo client) is created.
 * A plain @Component would race against eager autoconfiguration beans and
 * could lose, producing a confusing low-level error instead of this one.
 *
 * Never logs actual secret values — only which variable is missing/unsafe.
 */
public class ProductionSafetyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String DEV_JWT_SECRET = "dev-secret-change-me-dev-secret-change-me";
    private static final int MIN_JWT_SECRET_LENGTH = 32;
    private static final String DEV_MINIO_KEY = "minioadmin";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }

        List<String> problems = new ArrayList<>();

        checkMongo(environment, problems);
        checkJwt(environment, problems);
        checkMinio(environment, problems);
        checkCors(environment, problems);
        checkMail(environment, problems);
        checkFrontendUrl(environment, problems);
        checkOcr(environment, problems);

        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder(
                    "Refusing to start with profile 'prod': unsafe or missing configuration detected:\n");
            for (String problem : problems) {
                message.append("  - ").append(problem).append('\n');
            }
            throw new IllegalStateException(message.toString());
        }
    }

    private void checkMongo(ConfigurableEnvironment env, List<String> problems) {
        String uri = get(env, "spring.mongodb.uri");
        if (!StringUtils.hasText(uri)) {
            problems.add("MONGODB_URI is not set.");
        } else if (containsLocalhost(uri)) {
            problems.add("MONGODB_URI points at localhost/127.0.0.1 — set a real production MongoDB URI.");
        }
    }

    private void checkJwt(ConfigurableEnvironment env, List<String> problems) {
        String secret = get(env, "app.jwt.secret");
        if (!StringUtils.hasText(secret)) {
            problems.add("APP_JWT_SECRET is not set.");
        } else if (secret.equals(DEV_JWT_SECRET)) {
            problems.add("APP_JWT_SECRET is still the development placeholder — set a real secret.");
        } else if (secret.length() < MIN_JWT_SECRET_LENGTH) {
            problems.add("APP_JWT_SECRET is too short (" + secret.length()
                    + " chars) — use at least " + MIN_JWT_SECRET_LENGTH + " random characters.");
        }
    }

    private void checkMinio(ConfigurableEnvironment env, List<String> problems) {
        String url = get(env, "minio.url");
        String accessKey = get(env, "minio.access-key");
        String secretKey = get(env, "minio.secret-key");

        if (!StringUtils.hasText(url)) {
            problems.add("MINIO_URL is not set.");
        } else if (containsLocalhost(url)) {
            problems.add("MINIO_URL points at localhost/127.0.0.1 — set a real production MinIO endpoint.");
        }

        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            problems.add("MINIO_ACCESS_KEY / MINIO_SECRET_KEY are not both set.");
        } else if (DEV_MINIO_KEY.equals(accessKey) && DEV_MINIO_KEY.equals(secretKey)) {
            problems.add("MINIO_ACCESS_KEY/MINIO_SECRET_KEY are still the default dev credentials ('minioadmin').");
        }
    }

    private void checkCors(ConfigurableEnvironment env, List<String> problems) {
        String raw = get(env, "app.cors.allowed-origins");
        List<String> origins = StringUtils.hasText(raw)
                ? Arrays.stream(raw.split(",")).map(String::trim).collect(Collectors.toList())
                : List.of();

        if (origins.isEmpty() || origins.stream().allMatch(String::isBlank)) {
            problems.add("APP_CORS_ALLOWED_ORIGINS is not set — production requires an explicit origin list.");
        } else if (origins.stream().anyMatch(this::containsLocalhost)) {
            problems.add("APP_CORS_ALLOWED_ORIGINS contains a localhost/127.0.0.1 origin — use real frontend origin(s).");
        }
    }

    private void checkMail(ConfigurableEnvironment env, List<String> problems) {
        String enabledValue = get(env, "app.mail.enabled");
        boolean mailEnabled = enabledValue == null || Boolean.parseBoolean(enabledValue);
        if (!mailEnabled) {
            return;
        }
        String username = get(env, "spring.mail.username");
        String password = get(env, "spring.mail.password");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            problems.add("MAIL_ENABLED is true but MAIL_USERNAME/MAIL_PASSWORD are not both set.");
        }
    }

    private void checkFrontendUrl(ConfigurableEnvironment env, List<String> problems) {
        String frontendUrl = get(env, "app.frontend-url");
        if (!StringUtils.hasText(frontendUrl)) {
            problems.add("APP_FRONTEND_URL is not set.");
        } else if (containsLocalhost(frontendUrl)) {
            problems.add("APP_FRONTEND_URL points at localhost/127.0.0.1 — set the real production frontend URL.");
        }
    }

    private void checkOcr(ConfigurableEnvironment env, List<String> problems) {
        String ocrUrl = get(env, "ocr.api.url");
        if (!StringUtils.hasText(ocrUrl)) {
            problems.add("OCR_API_URL is not set.");
        } else if (containsLocalhost(ocrUrl)) {
            problems.add("OCR_API_URL points at localhost/127.0.0.1 — set the real production OCR service URL.");
        }
    }

    private boolean containsLocalhost(String value) {
        String lower = value.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1");
    }

    /**
     * Environment.getProperty() throws PlaceholderResolutionException (rather
     * than returning null) when a property's value contains an unresolved
     * nested placeholder (e.g. `${MONGODB_URI}` with no env var set and no
     * default). Treat that the same as "not set" instead of letting it
     * propagate as an unrelated-looking crash.
     */
    private String get(ConfigurableEnvironment env, String key) {
        try {
            return env.getProperty(key);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
