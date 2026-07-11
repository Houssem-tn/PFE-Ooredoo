# syntax=docker/dockerfile:1
#
# Multi-stage build for the Spring Boot backend.
#   1. build  — Maven wrapper, Java 17, compiles the application AND
#      compiles (but does not run) the test suite here — see the
#      -DskipTests note on the `clean package` step below for why. Tests
#      themselves are still fully mandatory: this Docker job only runs
#      after CI's separate `validate` job (a real MongoDB service
#      container, `mvnw clean verify`) has already passed — see this
#      repo's .github/workflows/ci.yml `docker:` job's `needs:`.
#   2. layers — splits the repackaged fat jar into Spring Boot's standard
#      layers (dependencies / spring-boot-loader / snapshot-dependencies /
#      application) so that rebuilding the image after an app-only code
#      change reuses the cached dependencies layer instead of re-pushing
#      ~80MB of unchanged third-party jars.
#   3. runtime — JRE-only, non-root, only the extracted layers copied in.
#      No source, no Maven caches, no build tooling, no secrets.

############################################
# Stage 1: build + test
############################################
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Wrapper + POM first so `dependency:go-offline` is its own cached layer —
# it only re-runs when pom.xml (or the wrapper) actually changes, not on
# every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# distributionSha256Sum (.mvn/wrapper/maven-wrapper.properties) is
# INTENTIONALLY stripped for this build stage only — real Docker-build
# investigation, not a shortcut. mvnw's own checksum verification of the
# Maven distribution zip failed 100% of the time on GitHub-hosted Actions
# runners, across every variant tried: the pinned hash was independently
# re-verified correct 3+ times against Apache's own published .sha512;
# forcing curl over wget made no difference; a 3-attempt retry loop failed
# identically every time (ruling out a transient network blip); a manual
# `curl` of the exact same URL in the exact same failing job produced
# byte-correct output; MVNW_VERBOSE=true showed mvnw computing the correct
# URL and using curl with realistic transfer timing — and it still failed
# the checksum every time. Could not reproduce locally either, including
# explicitly under dash (this environment's real /bin/sh, unlike Git
# Bash's mvnw invocations which always run under bash). Root cause was not
# found; disabling this specific redundant check (Maven Central's own
# artifact signing is the primary integrity guarantee either way) was a
# deliberate, user-confirmed call after that investigation — not a
# default. distributionSha256Sum stays fully enforced for local `mvnw`
# usage and is still checked for presence by CI's env-validate job; only
# this container's fetch of it is skipped.
RUN sed -i '/^distributionSha256Sum=/d' .mvn/wrapper/maven-wrapper.properties \
    && chmod +x mvnw \
    && ./mvnw -q -B dependency:go-offline

COPY src/ src/
# -DskipTests (not -Dmaven.test.skip=true): test *sources* still compile
# here, so a test that no longer compiles still breaks this build — only
# actually *running* them is skipped. Running them here was never
# meaningful: PfeApplicationTests.contextLoads (and other @SpringBootTest
# classes) need a real MongoDB connection, which this isolated `docker
# build` container has no way to provide the way CI's `validate` job's
# MongoDB service container does — confirmed by an actual failed run
# (PfeApplicationTests.contextLoads timing out on
# com.mongodb.MongoSocketOpenException: Connection refused to
# localhost:27017). The real, blocking test run already happened in
# `validate`, which this job's `needs:` requires to have passed first;
# this was 100% redundant re-execution in an environment that can only
# ever fail it, not a second layer of protection.
RUN ./mvnw -q -B clean package -DskipTests

############################################
# Stage 2: extract layered jar
############################################
FROM eclipse-temurin:17-jre-jammy AS layers
WORKDIR /layers
COPY --from=build /build/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

############################################
# Stage 3: runtime
############################################
FROM eclipse-temurin:17-jre-jammy AS runtime

# curl is only present so the container HEALTHCHECK below has an HTTP
# client; nothing else is installed and apt's own state is removed
# immediately so it isn't left sitting in the image. `apt-get upgrade`
# pulls whatever security patches are current at build time for packages
# already in the base image — the jammy tag is a moving tag, not rebuilt
# every time an upstream package gets patched (confirmed in practice on
# the sibling frontend image's Alpine base: a freshly-pulled image still
# had an already-fixed OpenSSL CVE — see Pfe-Oreedoo-Front/Dockerfile).
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Fixed, non-root UID/GID (no login shell, no home directory needed).
RUN groupadd --gid 1000 spring \
    && useradd --uid 1000 --gid spring --shell /usr/sbin/nologin --no-create-home spring

WORKDIR /app

COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./

RUN chown -R spring:spring /app
USER spring:spring

# Documents the app's own default (application.properties: SERVER_PORT is
# read at runtime via env var) — changing this alone does not change the
# port the JVM listens on, only what's advertised to `docker inspect`.
EXPOSE 8080

# Picked up automatically by the `java` launcher without needing a shell
# wrapper around ENTRYPOINT — lets operators pass extra JVM flags (e.g.
# -Xmx512m) via `docker run -e JAVA_TOOL_OPTIONS=...` without touching the
# image. Empty by default.
ENV JAVA_TOOL_OPTIONS=""
# Image-level default for standalone `docker run` (no orchestrator setting
# it explicitly). docker-compose.yml overrides this per environment — see
# DOCKER.md "Which Spring profile does Compose use, and why" for why the
# default local Compose stack actually runs `dev`, not `prod`.
ENV SPRING_PROFILES_ACTIVE=prod

# Distinct from Compose-level health checks (which can probe from outside
# the container): this is the container's own self-check, usable even when
# run standalone with `docker run`. /actuator/health/readiness reflects
# both "process started" and "MongoDB reachable" (see
# management.endpoint.health.group.readiness.include in
# application.properties) — a plain TCP-open check would miss a backend
# that started but can't reach its database.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fs "http://localhost:${SERVER_PORT:-8080}/actuator/health/readiness" || exit 1

# Exec form so SIGTERM (docker stop / compose down) reaches the JVM
# directly as PID 1, enabling server.shutdown=graceful to actually drain
# in-flight requests instead of being swallowed by a shell wrapper.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
