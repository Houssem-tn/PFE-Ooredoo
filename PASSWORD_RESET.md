# Password Reset (Forgot / Reset Password)

Self-service password reset for the existing authentication system. Reuses the
existing `AppUser`, `UserRepository`, `AuthController`, `PasswordEncoder`,
`JwtService`/filters, `EmailService`, notification and (SLF4J) audit
infrastructure — it is **not** a separate auth system.

## Endpoints (public — permitted by the existing `/api/v1/auth/**` rule)

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/forgot-password` | `{ "email": "..." }` | `200 { "message": "If an account exists for this email, a password-reset link has been sent." }` (identical for existing / unknown / disabled / unapproved) |
| `POST` | `/api/v1/auth/reset-password` | `{ "token", "newPassword", "confirmPassword" }` | `200 { "message": "Your password has been reset successfully." }` |

Reset failures return a generic `400 Invalid or expired reset link` (unknown /
expired / used / revoked tokens are indistinguishable). Brute-force on the reset
endpoint returns `429`.

## Token model

- Raw token: 32 secure-random bytes, URL-safe Base64 (no padding) — 256-bit entropy.
- Stored: **only** the SHA-256 hash (`password_reset_tokens.tokenHash`). The raw
  token exists only long enough to build the email link; it is never stored or logged.
- Single-use (atomic `findAndModify` ACTIVE→USED claim), expires in 15–30 min,
  previous unused tokens revoked on each new request.
- Indexes (created programmatically by `PasswordResetIndexConfig`): unique
  `tokenHash`, `userId+status`, and a **TTL** on `expiresAt`. Expiry is also
  re-validated in the service because TTL cleanup is not immediate.

## Session invalidation

Single mechanism: **`AppUser.tokenVersion`**. Every issued JWT embeds the version
(`ver` claim); a completed reset increments it, so all previously issued access
tokens are rejected on the next request by **both** the REST filter
(`JwtAuthFilter`) and the WebSocket interceptor (`WebSocketAuthChannelInterceptor`).
Legacy users/tokens (no field / no claim) resolve to `0`, so the upgrade never
invalidates a currently valid session. `passwordChangedAt` is stored for
audit/notification display only.

## Account controls (never bypassed)

A reset only changes `password`, `passwordChangedAt`, `tokenVersion`. It never
changes `role`, `status`, `enabled` or any assignment. **Chosen business rule:**
only `APPROVED` + `enabled` accounts may receive or complete a reset; every other
state gets the same generic response and no email.

## Configuration (`app.password-reset.*`, all with safe dev defaults)

| Property | Env var | Default |
| --- | --- | --- |
| `token-expiration-minutes` | `APP_PWRESET_TOKEN_EXPIRATION_MINUTES` | `20` (clamped to 15–30) |
| `max-active-tokens-per-user` | `APP_PWRESET_MAX_ACTIVE_TOKENS` | `3` |
| `request-cooldown-seconds` | `APP_PWRESET_REQUEST_COOLDOWN_SECONDS` | `60` |
| `max-requests-per-email-per-hour` | `APP_PWRESET_MAX_REQUESTS_PER_EMAIL` | `5` |
| `max-requests-per-ip-per-hour` | `APP_PWRESET_MAX_REQUESTS_PER_IP` | `20` |
| `max-reset-attempts-per-ip-per-hour` | `APP_PWRESET_MAX_RESET_ATTEMPTS_PER_IP` | `30` |
| `email-sender-name` | `APP_PWRESET_EMAIL_SENDER_NAME` | `Ooredoo Field Service Platform` |

The reset link is built from **`app.frontend-url`** (`APP_FRONTEND_URL`) only —
never from a request `Host` header. Production already **requires**
`APP_FRONTEND_URL` (no localhost) via `ProductionSafetyEnvironmentPostProcessor`,
which fails startup if it is missing/unsafe. The email **sender address** reuses
the existing `app.mail.from`. No new secrets are introduced.

## Known limitations / production notes

- **Rate limiter is in-memory** (`PasswordResetRateLimiter`). In a multi-replica
  (Kubernetes) deployment each pod keeps its own counters, so the effective
  global limit is `perPodLimit × replicas`. For strict global limits, back the
  counters with a shared store (e.g. Redis `INCR`/`EXPIRE`) behind the same
  interface — no caller changes required.
- **No MongoDB multi-document transactions are assumed** (dev is standalone).
  Single-use is guaranteed by an atomic single-document `findAndModify` claim;
  the remaining writes use safe ordering (password + `tokenVersion` bump on the
  single user document first, then token bookkeeping).
- Reset does **not** auto-authenticate the user — the client redirects to login.
