# syntax=docker/dockerfile:1
#
# Multi-stage build for the Spring Boot backend.
#   1. build  — Maven wrapper, Java 17, compiles AND runs the full test
#      suite (mvnw clean package does not skip tests; the build fails here,
#      not in production, if a test regresses).
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

# mvnw prefers wget over curl when both are present; forced curl here
# (removed wget) on the theory that was the difference from local testing.
# It wasn't: a diagnostic step (since removed) proved the raw download's
# sha256 matches distributionSha256Sum exactly, every time, including in
# this exact environment — the file and the value in
# maven-wrapper.properties are both genuinely correct. The two real CI
# failures were still intermittent: the checksum-verified value is right,
# but mvnw's OWN download (a separate request from the diagnostic's) isn't
# guaranteed to land on it every single time — consistent with a transient
# hiccup somewhere between here and repo.maven.apache.org (a CDN edge, a
# network blip), not a deterministic corruption. Keeping curl (still no
# reason to prefer wget's code path, which was never verified working at
# all) and wrapping the wrapper's own download+verify in a small retry
# loop — this does NOT weaken the checksum check itself (still runs in
# full every attempt, still fails the build if every retry's checksum is
# wrong), it only tolerates a one-off bad download the same way a flaky
# network blip should be tolerated rather than treated as a permanent
# integrity failure.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get remove -y --purge wget \
    && rm -rf /var/lib/apt/lists/*

# Wrapper + POM first so `dependency:go-offline` is its own cached layer —
# it only re-runs when pom.xml (or the wrapper) actually changes, not on
# every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && for attempt in 1 2 3; do \
         ./mvnw -q -B dependency:go-offline && break; \
         echo "mvnw dependency:go-offline failed (attempt $attempt/3)" >&2; \
         if [ "$attempt" = 3 ]; then exit 1; fi; \
         sleep 5; \
       done

COPY src/ src/
RUN ./mvnw -q -B clean package

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
