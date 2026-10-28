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
# Requirements: JDK 21, Maven 3.9+
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

Demo OIDC client registered for local testing: `mes-web` (public client, PKCE required).

## Roadmap

- [x] M0 skeleton: multi-module build, three security filter chains, discovery reachable
- [x] M1 login closed loop: JSON login API, SPA CSRF, `/api/auth/me`, JSON logout
- [x] M1.5 persistence: dual-mode wiring (JDBC / in-memory fallback), Flyway schema
      (official SAS 7 DDL + platform tables), persistent JWK store with rotation history
- [ ] M3 user domain: users / orgs / roles CRUD + HR sync
- [ ] M4 admin UI: Vue 3 SPA (login + admin console)
- [ ] M5 manufacturing: WeCom scan login, device flow, kick-off-line
- [ ] M6 hardening: load test (500+ TPS), audit reports, monitoring

See the [development doc](docs/) (Chinese) for the full specification.

## License

TBD
