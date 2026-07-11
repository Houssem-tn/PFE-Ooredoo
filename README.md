# PFE-Ooredoo
Projet de fin d'étude

## Configuration

Spring profiles: `dev` (default), `test` (auto-active during `mvnw test`), `prod`.
Profile-specific settings live in `application-{dev,test,prod}.properties`; shared
settings live in `application.properties`. See `.env.example` for the full list of
environment variables.

### Selecting a profile

**bash / macOS / Linux**
```bash
export SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

**PowerShell**
```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

If `SPRING_PROFILES_ACTIVE` is not set, the app defaults to `dev`.

### Development

No real credentials required. Point `MONGODB_URI` at a local MongoDB (defaults to
`mongodb://localhost:27017/pfe`) and, optionally, a local MinIO instance
(`http://localhost:9000`, `minioadmin`/`minioadmin` by default). Real email sending is
off by default (`MAIL_ENABLED=false`); set it to `true` plus `MAIL_USERNAME`/
`MAIL_PASSWORD` to test real email flows.

```bash
cp .env.example .env   # fill in values you want to override
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

```powershell
Copy-Item .env.example .env   # fill in values you want to override
Get-Content .env | Where-Object { $_ -notmatch '^#' -and $_.Trim() } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  Set-Item -Path "Env:$name" -Value $value
}
.\mvnw.cmd spring-boot:run
```

### Test

`mvnw test` automatically activates the `test` profile
(`src/test/resources/application.properties`) — no manual setup needed. It uses a
dedicated `pfe_test` MongoDB database (never the dev/prod database), a fixed
test-only JWT secret, and never sends real email.

```bash
./mvnw test
```

### Production

Every environment-specific value is **required** via environment variable — there is
no localhost/dev fallback. `ProductionSafetyValidator` fails startup immediately with
a clear message (naming the missing/unsafe variable, never its value) if any required
variable is missing or still looks like a dev default (e.g. the placeholder JWT
secret, `minioadmin` MinIO credentials, a `localhost` URL). Required: `MONGODB_URI`,
`APP_JWT_SECRET` (32+ random chars), `MINIO_URL`/`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`,
`APP_CORS_ALLOWED_ORIGINS`, `APP_FRONTEND_URL`, `OCR_API_URL`, and (only if
`MAIL_ENABLED=true`) `MAIL_USERNAME`/`MAIL_PASSWORD`.

```bash
export SPRING_PROFILES_ACTIVE=prod
export MONGODB_URI=... APP_JWT_SECRET=... MINIO_URL=... MINIO_ACCESS_KEY=... \
       MINIO_SECRET_KEY=... APP_CORS_ALLOWED_ORIGINS=... APP_FRONTEND_URL=... OCR_API_URL=...
./mvnw spring-boot:run
```

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:MONGODB_URI = "..."; $env:APP_JWT_SECRET = "..."; $env:MINIO_URL = "..."
$env:MINIO_ACCESS_KEY = "..."; $env:MINIO_SECRET_KEY = "..."
$env:APP_CORS_ALLOWED_ORIGINS = "..."; $env:APP_FRONTEND_URL = "..."; $env:OCR_API_URL = "..."
.\mvnw.cmd spring-boot:run
```

### Secrets

Never commit real credentials. `.env`, `application-local*.properties`, and
`application-secrets*.properties` are git-ignored for this purpose — see
`.gitignore` and `CONTRIBUTING.md`.
