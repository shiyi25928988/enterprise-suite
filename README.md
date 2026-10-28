# enterprise-suite

Enterprise unified authentication platform built on **Spring Authorization Server 7** with a built-in user & organization service — providing **OAuth 2.1 / OIDC SSO**, RBAC and multi-plant support for internal office and manufacturing systems.

> 企业统一认证平台：基于 Spring Authorization Server 7 + 内置用户/组织服务，为办公与制造系统提供 OAuth 2.1 / OIDC 单点登录、RBAC 权限与多厂区支持。

[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-TBD-blue)](#license)

## What it does

- **OAuth 2.1 / OIDC protocol endpoints** — authorization code + PKCE, client credentials, refresh token rotation, device flow (for shop-floor dashboards), discovery & JWKS
- **User service (same process)** — users, organizations (company → plant → workshop → line), roles & permissions
- **Single deployment** — a modular monolith: the auth server reads user data in-process, no remote calls, no data sync

## Project structure

```
enterprise-suite/
└── auth-platform/
    ├── auth-common/    shared types: ApiResult, ErrorCode, base exceptions
    ├── auth-user/      user domain: DTO / service / REST API (SAS-agnostic)
    └── auth-server/    executable: SAS 7 wiring + user service assembly
```

Dependency direction is enforced at compile time: `auth-server → auth-user → auth-common`, never backwards.

## Quick start

```bash
# Requirements: JDK 21, Maven 3.9+, Node 20+ (frontend only)
mvn clean package -DskipTests

# Option A: in-memory mode (zero external dependencies, dev/demo)
java -jar auth-platform/auth-server/target/auth-server-*.jar

# Option B: JDBC mode (Flyway creates SAS + platform schema on first boot)
DB_USER=root DB_PWD=secret \
java -jar auth-platform/auth-server/target/auth-server-*.jar --spring.profiles.active=jdbc
```

Then:

- Discovery: `http://localhost:9000/.well-known/openid-configuration`
- Login (skeleton-stage default page): `admin / admin123`
- User API (requires session): `http://localhost:9000/api/v1/users`

Demo OIDC client registered for local testing: `mes-web` (public client, PKCE required),
redirect URI `http://localhost:8081/callback` (the Vite dev server port).

### Verified build (2026-10-28)

Built and smoke-tested against JDK 21.0.12 + Maven 3.9.16 + Boot 4.1.2 / Security 7.1.2:

| Check | Result |
|---|---|
| `mvn clean package -DskipTests` | BUILD SUCCESS (5 modules, 38 MB fat jar) |
| `npm install && npm run build` | 110 modules, main bundle 165 KB |
| Boot in memory mode | started in ~8 s on :9000 |
| `/.well-known/openid-configuration`, `/oauth2/jwks` | 200 |
| `/actuator/health` (liveness/readiness) | 200 `UP`, unauthenticated |
| JSON login `admin/admin123` → `/api/auth/me` | 200, authorities `[ROLE_ADMIN]` |
| Wrong password / no session | 401 `40101` / `40100` |
| Non-admin on `/api/v1/users` | 403 `40300` |
| Full OIDC: authorize → consent → code → token (PKCE S256) | 200, `id_token` carries `auth_time` |
| Logout | session invalidated, subsequent `/me` → 401 |

Not exercised (needs external infrastructure): JDBC profile (MySQL 8 + Flyway
migrations), AD/LDAP and WeCom providers (disabled by default).


### Frontend SPA (auth-web)

> Access control: the admin console is restricted to users holding the
> `ADMIN` role — enforced client-side by the router guard (`/403` fallback)
> and server-side by `@PreAuthorize("hasRole('ADMIN')")` on every
> `/api/v1/**` endpoint, reads included.

```bash
cd auth-web
npm install
npm run dev        # http://localhost:8081 — login with admin / admin123
```

In dev, Vite proxies `/api`, `/oauth2`, `/.well-known` etc. to the backend on :9000,
so browser traffic is same-origin — the same topology as production.

## Roadmap

- [x] M0 skeleton: multi-module build, three security filter chains, discovery reachable
- [x] M1 login closed loop: JSON login API, SPA CSRF, `/api/auth/me`, JSON logout
- [x] M1.5 persistence: dual-mode wiring (JDBC / in-memory fallback), Flyway schema
      (official SAS 7 DDL + platform tables), persistent JWK store with rotation history
- [x] M3 user domain: JDBC auth source, login audit + auto-lock,
      users / orgs / roles management CRUD
- [x] M4 login SPA: Vue 3 + Vite, session login, CSRF cookie mode,
      authorization-code + PKCE demo (in-memory tokens only)
- [x] M4b admin console: users (paged CRUD, roles, reset password, status),
      org tree, roles management
- [x] M5a auth sources: AD/LDAP provider (auth.ldap.*), WeCom QR scan login
      (auth.wecom.*, auto-bind on first login), device-flow demo client
      `mes-screen` (8h token for workshop displays)
- [x] M5b MFA: TOTP second factor (RFC 6238), challenge-based two-step login,
      enrollment UI under Security settings; per-user opt-in
- [ ] M5c: DingTalk, badge QR, per-device token policies, anomaly detection
- [ ] M6 hardening: load test (500+ TPS), audit reports, monitoring

See the [development doc](docs/) (Chinese) for the full specification.

## License

TBD
