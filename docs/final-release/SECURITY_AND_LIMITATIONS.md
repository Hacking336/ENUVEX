# Security and Limitations

**JSM — final release.** This document describes only protections that exist in the code, states what they do not cover, and ranks the remaining risks. No secret, password, access code or full database URL appears here.

---

## 1. Mechanisms that exist

### 1.1 Authentication

| Control | Implementation | Effect |
| --- | --- | --- |
| Password hashing | `BCryptPasswordEncoder` (cost 10) | Hashes are one-way; the hash is never returned by any endpoint |
| Token | JJWT 0.12.5, HS256, `JWT_SECRET` with **no default** | The application refuses to start without a strong secret, so it can never sign with a value published in the repository |
| Token lifetime | `JWT_EXPIRATION_MS`, default 1 hour | Short-lived access tokens |
| Credential errors | Identical message for unknown email and wrong password; access-code failures also generic | An attacker cannot enumerate accounts or confirm a guessed code |
| Administrator shortcut | `admin` / `admin++` accepted **only** when the password field equals `JSM_ADMIN_ACCESS_CODE`, compared with `MessageDigest.isEqual` (constant time) | The name-only takeover found in Phase 7 is closed; an unset variable disables the shortcut |
| Rate limiting | Persistent counters in `rate_limit_counters` (V9), applied to sign-in, registration, report and application submission | Password spray and form spam are slowed without an in-memory counter that a restart would clear |

### 1.2 Session handling

- The JWT filter re-reads the account on **every** request (`CustomUserDetailsService`) and requires `isEnabled`, `isAccountNonLocked` and `isAccountNonExpired`.
- Consequence: suspension takes effect immediately. A token issued before the suspension stops working; restoring the account makes the same token work again until its expiry.
- Invalid, expired or tampered tokens are treated as anonymous, producing a clean `401`, never a `500`.

### 1.3 Authorization

- URL rules in `SecurityConfig` (public pages, health, enums, `GET /api/geo/**`, `GET /api/jobs/**`, uploads, API docs; everything else authenticated) **plus** class-level `@PreAuthorize` on role controllers.
- Ownership checks in services: an employer reaches only its own posts, applicants and analytics; a seeker reaches only its own applications. Foreign records answer `403`, so existence is not leaked.
- Administrator-only surface: verification, moderation, reports, suspicious listings, user suspension, audit logs, learning resources, platform analytics.
- Self-protection rules: an administrator cannot suspend their own account or the last remaining administrator.
- Audit log has no write/update/delete route: the table is append-only through the API.

### 1.4 Input handling and API behaviour

| Control | Detail |
| --- | --- |
| Bean validation | `spring-boot-starter-validation` on request bodies; invalid input answers `400` with a message |
| Parameter types | Enums and numbers are parsed by Spring, so an unknown municipality or a non-numeric id answers `400` |
| Page-size cap | `MAX_PAGE_SIZE = 100` in `JobService`; admin job list clamped to 1–100 |
| SQL injection | All data access goes through Spring Data JPA / parameterised queries; no string-concatenated SQL |
| XSS in rendered lists | Pages escape values before inserting them into HTML (`esc()` / `ROLE.esc`), including job titles, skill names and notification text |
| CSV formula injection | The analytics export neutralises cells starting with `=`, `+`, `-`, `@`, tab or CR |
| Mass assignment | Request DTOs carry only accepted fields; ids, roles, moderation states and ownership come from the token, never from the body |
| File uploads | Multipart only at registration, 5 MB limit, content type stored with the bytes, served by filename from the database |
| Error bodies | Generic messages (`Internal server error`); stack traces are logged, never returned |
| Correct status codes | 400 / 401 / 403 / 404 / 405 / 409 / 429 / 500 — no blanket 500s for client mistakes (Phase 7 fix) |

### 1.5 Transport and browser hardening

- HTTPS is terminated by the host (Render), and the production database connection requires TLS (`sslmode=require`).
- Response headers on every request: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: geolocation=(self), camera=(), microphone=(), payment=()`, `Cache-Control: no-cache, no-store` on API responses.
- CSRF: the API is stateless and authenticated by an `Authorization` header, not by cookies, so cross-site request forgery cannot ride an ambient session; CSRF protection is therefore disabled in Spring Security. CORS accepts the request's own host plus fixed origins (production domain, custom domain, localhost, Capacitor), so a foreign site cannot read responses.
- Geopositioning ("Near me") is used only to measure distance for that search; it is never stored or shared with employers, and the header policy restricts the API surface to same-origin use.

### 1.6 Operational safety

- Flyway `clean-disabled: true`; every migration additive.
- Health endpoint performs a real database readiness query and answers `503` when unavailable.
- Rate limiter **fails open** on a database error, so a counter-table problem cannot lock users out.
- Deployment secrets come from the host environment (`DB_*`, `JWT_SECRET`, `JSM_ADMIN_ACCESS_CODE`); none is committed.
- Uploads live in PostgreSQL, avoiding a world-writable volume.

## 2. Known limitations (current release)

| # | Limitation | Why it exists | Risk if unchanged |
| --- | --- | --- | --- |
| 1 | **JWT stored in `localStorage`** | The client is plain JavaScript with no cookie/session infrastructure; moving to HttpOnly cookies means touching 30+ pages and the CSRF model | Any XSS anywhere on the origin can read the token. Mitigated by escaping and `no-store`, not eliminated |
| 2 | **No Content-Security-Policy** | Every page uses inline scripts and styles; a strict CSP needs nonces or hashes across all pages | A CSP would give defence-in-depth against injected script. Framing, sniffing and referrer are already restricted |
| 3 | **No refresh-token flow** | 1-hour tokens with no refresh endpoint | Users must sign in again after an hour; an unattended kiosk session ends |
| 4 | **Browser-side pagination on some admin lists** (`/employers`, `/users`, `/suspicious-listings`) | Changing them to server pages changes the response shape and would break the Phase 6 admin pages | Response size grows with the table; no data leak |
| 5 | **Swagger UI and `/api-docs` public** | Convenient during development | Route shapes are discoverable. No data or credentials are exposed; can be switched off in production |
| 6 | **Rate limiting is per client address** | The filter runs before authentication, so the account is not yet known | A shared network (school laboratory, office NAT) shares one budget; a distributed attacker with many addresses spreads out. Defaults are generous (20 sign-ins/min) and configurable |
| 7 | **Administrator seeded password is a public placeholder** | Migration V1 seeds a documented example hash | The operator must rotate it (Render environment / SQL update) or rely on `JSM_ADMIN_ACCESS_CODE`. The shortcut itself is disabled unless the code is set |
| 8 | **No audit of read operations** | Only moderation writes are audited | Who *viewed* sensitive data is not recorded |
| 9 | **No account lockout on repeated wrong passwords** | Only the address-based limiter exists | A patient attacker can try the limit every minute; strong passwords are the real defence |
| 10 | **No password policy beyond validation** | Registration validates presence/length only | Weak user passwords are possible |
| 11 | **Free hosting tier** | Cost | Instance sleeps; first request slower; no uptime guarantee |
| 12 | **Single instance** | Deployment size | No horizontal scaling, no failover |
| 13 | **No virus scanning of uploads** | No external service | An uploaded image is stored and served as-is; only images are accepted by the UI, and the endpoint serves stored bytes with the recorded content type |
| 14 | **No email/SMS verification or password reset** | Not implemented | A user cannot recover an account without an administrator |

## 3. Prioritised recommendations

| Priority | Recommendation | Effort | Why |
| --- | --- | --- | --- |
| **Before a larger public rollout** | 1. Rotate the seeded administrator password to a strong, unique value (or rely solely on the access code) | Minutes | The only credential that is documented publicly |
| | 2. Serve the SPA with a CSP (start in report-only, then enforce with nonces/hashes) | Days | Removes most script-injection impact given tokens in `localStorage` |
| | 3. Add per-account login throttling in addition to per-address | Days | Stops distributed password spray |
| | 4. Move the token to HttpOnly, SameSite cookies with a CSRF token | 1–2 weeks | Largest single security upgrade |
| | 5. Disable Swagger UI in production (`SPRINGDOC_ENABLED=false` or a profile) | Minutes | Reduces discoverability |
| | 6. Server-side pagination for the remaining admin lists | Days | Keeps responses bounded as data grows |
| **Before scaling data** | 7. PostgreSQL full-text or `pg_trgm` indexing for keyword search (currently `LIKE '%term%'`) | Days | Search stays fast as posts grow |
| | 8. Add regression/component tests for the analytics endpoints (currently verified by scripted checks) | Days | Protects the Phase 5 surface during future edits |
| **Operational** | 9. Automated database backups with a tested restore | Hours | Supabase offers them; confirm the retention policy |
| | 10. Log aggregation and alerting on 5xx and 429 rates | Hours | Detect abuse and outages |
| | 11. Dependency scanning in CI (`mvn dependency-check`, Dependabot) | Hours | Catches known CVE in Spring/JJWT/postgres driver |

## 4. What is explicitly not claimed

- No penetration test, independent audit or formal vulnerability assessment was performed.
- No compliance certification (GDPR, Data Privacy Act of 2012 registration, ISO 27001) is claimed; the platform stores personal data and an operator would need to assess obligations separately.
- No claim that all production workflows were tested: see `TESTING_AND_QA_REPORT.md` §7 for what remains blocked.
- No claim of perfect secrets hygiene in the repository's older history; the current tree contains no secrets, and the deployment notes recommend rotating anything ever committed in the past.
