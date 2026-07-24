package com.pfe.password_reset.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ensures the indexes backing the {@code password_reset_tokens} collection.
 * Created programmatically (like {@code CaseIndexConfig}) because the project
 * does not enable annotation-based auto index creation. {@code ensureIndex} is
 * idempotent, so this is safe to run on every startup and against a collection
 * that already holds historical documents.
 *
 * <p>The TTL index on {@code expiresAt} lets MongoDB reap expired token
 * documents automatically. TTL cleanup runs on a background sweep (~every 60s)
 * and is therefore not immediate — the service still validates expiry
 * explicitly on every use.</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordResetIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var ops = mongoTemplate.indexOps("password_reset_tokens");

            // Fast, unique lookup by the presented (hashed) token. Unique also
            // enforces that two tokens can never collide on the same hash.
            ops.ensureIndex(new Index()
                    .on("tokenHash", Sort.Direction.ASC)
                    .unique()
                    .named("pwreset_tokenHash_unique"));

            // Revoking a user's previous unused tokens / counting active tokens.
            ops.ensureIndex(new Index()
                    .on("userId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .named("pwreset_userId_status"));

            // TTL: delete a token document once its expiry passes. expireAfter=0
            // means "expire exactly at the value stored in expiresAt".
            ops.ensureIndex(new Index()
                    .on("expiresAt", Sort.Direction.ASC)
                    .expire(Duration.ZERO)
                    .named("pwreset_expiresAt_ttl"));

            log.info("password_reset_indexes_ensured collection=password_reset_tokens");
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure password reset indexes: {}", ex.getMessage());
        }
    }
}
