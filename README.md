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
├── auth-platform/
│   ├── auth-common/    shared types: ApiResult, ErrorCode, base exceptions
│   ├── auth-user/      user domain: DTO / service / REST API (SAS-agnostic)
│   ├── auth-workflow/  workflow: embedded Flowable 8, approval API, AI node
│   └── auth-server/    executable: SAS 7 wiring + user + workflow assembly
├── auth-web/           admin console SPA (Vue 3 + Vite)
├── bench/              single-file JDK 21 load tools (java bench/X.java)
└── deploy/             docker-compose: Prometheus + Grafana, pre-provisioned
                        datasource and auth-platform dashboard
```

Dependency direction is enforced at compile time: `auth-server → auth-workflow → auth-user → auth-common`, never backwards.

## Quick start

```bash
# Requirements: JDK 21, Maven 3.9+, Node 20+ (frontend only)
mvn clean package -DskipTests

# Option A: in-memory mode (zero external dependencies, dev/demo)
java -jar auth-platform/auth-server/target/auth-server-*.jar

# Option B: JDBC mode (Flyway creates SAS + platform schema on first boot)
DB_USERNAME=root DB_PASSWORD=secret \
java -jar auth-platform/auth-server/target/auth-server-*.jar --spring.profiles.active=jdbc

# Option C: full production mode — MySQL persistence + Redis-backed sessions
# (login state survives restarts and is shared across instances)
java -jar auth-platform/auth-server/target/auth-server-*.jar \
     --spring.profiles.active=jdbc,redis \
     --spring.datasource.username=root --spring.datasource.password=secret \
     --spring.data.redis.host=redis.internal --spring.data.redis.password=secret
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

Not exercised (needs external infrastructure): AD/LDAP and WeCom providers
(disabled by default).

### Verified against real MySQL + Redis (2026-10-29, M6)

`jdbc,redis` profile against MySQL 8.4 / Redis on a LAN server:

| Check | Result |
|---|---|
| Flyway V1–V4 migrations on first boot | schema `auth_platform` at version 4 |
| JSON login → login audit row written to MySQL | `sys_login_log` insert ok |
| `/api/auth/me` with session | 200, authorities `[ROLE_ADMIN, FACTOR_PASSWORD]` |
| `/api/v1/users`, `/api/v1/orgs/tree` | data served from MySQL |
| Spring Session keys in Redis | `spring:session:sessions:<id>` present |
| `kill -9` the JVM → restart → same session cookie | session restored from Redis, user still logged in |
| Demo mode (no profile) after M6 wiring changes | unchanged: ~8 s boot, in-memory fallbacks active |

### M6b: monitoring + audit + load (2026-10-29)

- `/actuator/prometheus` (micrometer-registry-prometheus): JVM + HTTP request
  metrics out of the box, plus `auth_login_total{result,type}` business counter
  driven by Spring Security authentication events (covers password, MFA, LDAP,
  WeCom paths via one listener). Restrict scraper access via network policy.
- `GET /api/v1/login-logs` (ADMIN): paged audit query over `sys_login_log`
  with `username` / `success` / `start` / `end` filters; capped at 200/page.
- `bench/LoginBench.java`, `bench/GetBench.java`: single-file JDK 21 load tools.

Measured against the LAN MySQL 8.4 + Redis server (bench and server on the
same workstation, remote database):

| Scenario | Result |
|---|---|
| Full login flow (csrf → login → me → logout, BCrypt + MySQL audit write + Redis session write), 32 workers × 3,200 flows | 54 flows/s, **zero failures**, P50 543 ms / P99 1.3 s |
| Stateless OIDC reads (discovery document), 64 workers × 128,000 requests | **3,235 req/s**, zero errors, P50 16 ms / P99 102 ms |

### M7: monitoring stack + audit retention (2026-10-29)

- **`deploy/`** — one-shot Grafana + Prometheus stack:
  `cd deploy && GRAFANA_ADMIN_PASSWORD=... docker compose up -d`
  → Grafana on :3000 (datasource + the *Auth Platform* dashboard auto-provisioned:
  login success/failure rate from `auth_login_total`, HTTP rate & latency percentiles,
  JVM memory/threads/uptime). Point `deploy/prometheus.yml` at your auth-server
  instances (default `host.docker.internal:9000`).
  ⚠️ `/actuator/prometheus` is unauthenticated by design — firewall :9000 so only
  the Prometheus host can reach it.
- **Audit retention**: rows in `sys_login_log` older than
  `auth.audit.retention-days` (default 90) are purged daily at
  `auth.audit.cleanup-cron` (default 03:00). No-op in in-memory mode.

### M8: directory sync — WeCom / DingTalk (2026-10-29)

Loosely-coupled SPI (`io.github.shiyi.auth.user.sync.DirectoryProvider`):
each source is a bean activated **only** by configuration, the orchestrator
knows nothing about source APIs. Disabled by default — the platform behaves
exactly as before until you opt in.

```yaml
auth:
  sync:
    cron: "-"                    # scheduled full sync; "-" = off (default)
    dingtalk:
      enabled: true
      app-key: xxx  app-secret: xxx
      # base-url / token-url / root-name overridable (stub/test friendly)
    wecom:
      enabled: true
      corp-id: xxx  corp-secret: xxx   # contact-sync secret, not agent secret
```

Mapping rules: orgs matched by `(external_source, external_id)`, org_code =
`<source>:<extId>`, materialized path computed like local orgs; users matched
the same way, username = directory userid (collision with a local account is
skipped with a warning), random unusable password (these accounts log in via
QR/SSO), WeCom userid mirrored to `wecomUserid` so M5a QR-login auto-binds the
same account. Parent moves are reported, not applied (subtree path recompute).

Admin API: `GET /api/v1/sync` (sources + last run), `POST /api/v1/sync/{source}`
(trigger now). JDBC profile required. The admin console has a **目录同步** page
(`/sync`) wrapping both endpoints — sources, last-run stats, warnings, and a
one-click trigger.

Verified E2E against stub DingTalk + WeCom APIs: org trees imported with
correct materialized paths, users bound to their primary department, re-run is
idempotent, username conflicts skipped.

### M9: sync console page + Grafana alerting (2026-10-29)

- **auth-web**: 目录同步 admin page — per-source last-run stats, warning
  details, one-click sync trigger.
- **deploy/grafana/provisioning/alerting/**: auto-provisioned rules —
  *登录失败激增* (≥10 failures / 5 min, likely password spraying) and
  *认证服务实例离线* (scrape target down 2 min). Rules appear in Grafana on
  container start; configure a contact point (Alerting → Contact points) to
  actually receive notifications.



### Application access control + user center hardening (2026-10-29)

- **App registry & OIDC gate**: `sys_app` + `sys_app_role` (Flyway V6) — register
  client applications with an access policy (`ALL` / `ROLES`) and allowed role
  list. `AppAccessFilter` enforces it on `/oauth2/authorize` and (via the
  pending device authorization record) `/oauth2/device_verification`; denied
  users land on `/403?reason=…&app=…`. Admin console gains 应用管理 and
  我的应用 portal pages.
- **User center**: user detail API (`/users/{id}/detail` — roles, org path,
  login stats), org move (`PATCH /users/{id}/org`), role usage report +
  triple-guarded role deletion (deny while assigned / has users / in use by
  an app), operation audit log (`sys_operation_log`, V8) with its own admin
  query page (审计日志), SPA 404 fallback (navigable GETs forward to the SPA,
  `/api/**` get structured `40400`).
- **Device flow (OAuth 2.1)**: SAS 7.1.2 ships two gaps for PUBLIC device
  clients — no converter authenticates them on `/oauth2/device_authorization`
  and `PublicClientAuthenticationProvider` unconditionally runs the PKCE check,
  rejecting every `device_code` grant. Custom converter + provider (same
  client_id/public-client validation, no PKCE) close both; E2E verified
  end-to-end: device code → consent page → approval → token polling.
  Note: SAS rejects `openid` scope on device grants by design — device
  clients use business scopes (e.g. `profile`).
- **Password max-age**: `auth.password.max-age-days` (default 90) — expired
  passwords are flagged at login and funneled into the forced-change flow
  (`40103` on protected APIs) until changed.
- **Frontend**: `OrgTreeSelect` cascade component (list filter / edit form /
  detail org-move), replacing a flat select that only rendered tree roots.

### M10: CSV directory sync + webhook notifications (2026-10-30)

- **HR CSV source** (`auth.sync.csv.*`): a new `DirectoryProvider` turning a
  plain HR export into a directory — `type` column distinguishes org/user rows
  (`org: external_id,name,parent_external_id,sort_no` ·
  `user: external_id,name,mobile,employee_no,org_external_id`), RFC 4180
  quoting, UTF-8 default. The file is re-read on every sync run: drop a fresh
  export, hit `POST /api/v1/sync/csv`. Verified E2E: 3 orgs + 2 users imported
  with correct org binding, second run idempotent (all-zero delta).
- **Notification contact points** (`auth.notify.contacts`): webhook contacts
  receiving JSON POSTs for platform events — `account.locked` (auto-lock
  after failed logins), `sync.completed` / `sync.failed`. Delivery is
  bounded (3 s) and failures are swallowed: a dead webhook never breaks a
  login or a sync. Verified with a local stub receiver.

### W1: workflow module — approval flows (2026-10-30)

New Maven module **`auth-platform/auth-workflow`** deployed inside auth-server
(one service, one database). Embedded **Flowable 8.0** — its baseline is
Spring Framework 7 / Boot 4 / Jackson 3, i.e. exactly this project's stack
(Flowable 7.x tops out at Boot 3), wired programmatically against the
platform's own DataSource/transaction manager and creating its `ACT_` tables
on first boot.

- **Identity stays in the platform**: assignees are platform usernames,
  candidate groups are role codes straight from the BPMN
  (`flowable:candidateGroups="ADMIN"`) — nothing is mirrored into Flowable's
  own IDM tables. Task visibility = assignee OR one of the caller's roles;
  deciding a candidate task auto-claims it so "my done" stays meaningful.
- **Core API** (`/api/v1/flow/**`): deploy BPMN (ADMIN), definitions, start,
  todo / done / my-instances, task detail (variables + comments), claim,
  complete `{approved, comment}`. Start submissions are validated against the
  process's form schema — required fields and types (`string/number/boolean/
  date/enum/text`) are enforced server-side, not just in the SPA.
- **Business forms** (`sys_flow_form`, Flyway V9): JSON schema per process
  key, edited next to the designer, rendered dynamically at start time.
- **AI participant node**: service task
  `flowable:delegateExpression="${aiReviewDelegate}"` sits between start and
  the human approval. It sends the form payload to any OpenAI-compatible
  endpoint (`auth.flow.ai.*`, off by default), parses a structured
  `{suggestion, reason}` and stores `aiSuggestion` / `aiReason` as process
  variables — shown in the approver's task view. Every failure degrades to
  "unavailable"; a dead LLM never wedges an instance.
- **Notifications**: `TASK_CREATED` engine events publish `task.assigned`
  (task id/name, instance, candidate groups) through the M10 contact points.
- **Frontend**: 我的申请 (dynamic form + my instances), 审批中心 (todo/done,
  AI badge, comments), 流程管理 (bpmn-js designer with XML import/export,
  node name + candidate-roles editing, form schema editor).
- Ships the **请假审批** template (AI pre-review → manager approval →
  approve/reject gateway) on first boot.

Verified E2E against MySQL 8.4 + Redis: template auto-deploy, form validation
rejects missing required fields (40001), instance start → AI pre-review
(approve and reject branches via a stub LLM) → task completion → instance end
state `approved`/`rejected`, done list populated, `task.assigned` webhook
delivered. Custom BPMN deployed through the API is immediately startable.

### W2: workflow advanced — countersign, delegation, SLA, analytics (2026-10-30)

- **Countersign (会签)**: parallel multi-instance user tasks
  (`flowable:collection="${approvers}"`) — approvers collected as a
  comma-separated form field, converted to a `List` at start. Vote bookkeeping
  lives in `WorkflowService`: a process-level `countersignVotes` ledger, a
  first-reject veto flag (`csRejected`) that ends the block early, and an
  aggregate `approved` majority for the gateway after the MI block. Per-task
  progress (`2/3`) and the vote ledger surface in the SPA.
- **Delegation & transfer**: `delegate` (ownership handshake — delegatee
  answers via `resolveTask`, delegator completes; surfaced as
  `pending`/`resolved` with the owner in the task view), `transfer`
  (outright reassignment), `unclaim` (back to the candidate pool).
- **SLA reminders**: BPMN due dates via a `slaClock` expression bean
  (`flowable:dueDate="${slaClock.hours(48)}"`); a fixed-delay scanner
  (`auth.flow.sla.*`) publishes `task.overdue` webhooks once per task
  (comment-marker dedup). Polling rather than boundary timers — the engine's
  async executor is disabled in this deployment.
- **Analytics** (`/api/v1/flow/analytics/**`, ADMIN): overview (throughput,
  approval rate, average cycle time, overdue count), per-definition stats,
  per-node cycle times (min/avg/max), and a per-instance activity timeline.
  Decisions are counted only for finished instances — a running countersign's
  `approved` variable is scratch state, not an outcome.
- **Frontend**: 审批中心 gains delegation/transfer/unclaim actions, overdue
  badges and countersign progress; 我的申请 gains a timeline; new 流程分析
  admin page (stat cards, definition table, node-duration bars).
- Ships the **采购会签审批** template (AI pre-review → parallel countersign
  with one-veto rule → 48h SLA → approve/reject gateway) on first boot.

Verified E2E: countersign all-approve (votes ledger in instance detail) and
one-veto early exit, full delegation handshake (claim → delegate → resolve →
complete), transfer, `task.overdue` webhook delivered with due date and
overdue millis, analytics endpoints populated correctly, timeline shows every
activity including both countersign instances.

### M5c: DingTalk scan login, badge QR, per-device token policy, anomaly detection (2026-10-30)

- **DingTalk scan login** (`auth.dingtalk.*`, off by default):
  `/api/auth/dingtalk/config` hands the SPA the app key and redirect target;
  `/api/auth/dingtalk/login` exchanges the scanned `authCode` for an identity.
  `api.dingtalk.com` OAuth2 (`/v1.0/oauth2/userAccessToken` →
  `/v1.0/contact/users/me`) yields a **unionId**, which
  `oapi.dingtalk.com/topapi/user/getbyunionid` maps to the corporate userid. The
  first login auto-binds the local account (username match into
  `sys_user.dingtalk_userid`, unique); later logins resolve straight through the
  bound column. The client uses a raw `java.net.http.HttpClient` with an explicit
  `Content-Type: application/json` — the `RestClient` map-body builder delivered an
  empty `{}` to the stub, the same pitfall the M8 sync client already sidesteps.
- **Badge QR login** (工牌二维码): `sys_user.badge_code` holds a 32-byte
  `SecureRandom` token (base64url, 43 chars, unique). `/api/auth/badge/login` is
  passwordless but audited (type `BADGE`, code masked to its first 6 chars);
  `/api/auth/badge/rotate` reissues it and the previous code stops working
  immediately. The SPA renders `/api/auth/badge/mine` as a QR image client-side.
- **Per-device token policy**: `sys_app.access_token_ttl_minutes` /
  `device_code_ttl_minutes` flow into the registered client's `TokenSettings`, so a
  workshop screen can carry a 90-minute access token while the console keeps the
  platform default. Unset (or < 1) falls back to 30 / 10 minutes; on update a `0`
  explicitly clears a custom value back to the default, `null` keeps it.
- **Login anomaly detection** (`auth.anomaly.*`): two rules publish `login.anomaly`
  through the existing notification contact points — `new_location` (the client's
  /24 appears nowhere in the last 30 days of successful logins) and `brute_force`
  (failure count exactly at the threshold inside the window, so a burst alerts once
  instead of on every attempt). Detection runs **before** the audit row is written;
  otherwise the current attempt would pollute its own history.
  `ForwardedAwareWebAuthenticationDetails` makes `getRemoteAddress()` honour
  `X-Forwarded-For` rather than the proxy hop.
- **Error surface**: an `AuthenticationException` thrown inside a controller (invalid
  badge code, expired DingTalk code) is translated to `401 / 40101` by the global
  handler instead of surfacing as a 500.

Verified E2E against stub APIs: DingTalk first-login auto-bind followed by a
bound-column login, badge login plus rotation (old code rejected, new one accepted),
`new_location` fired once for a fresh /24 and not again from the same subnet,
`brute_force` fired exactly once at the fifth failure, and per-device TTLs read back
from `oauth2_registered_client.token_settings` (`PT1H30M` / `PT25M`).

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
- [x] M6 cluster session store: Redis-backed HTTP session (`redis` profile),
      servlet-session timeout retained; JDBC profile verified against MySQL 8.4
- [x] M6b hardening: Prometheus endpoint + auth metrics, login audit query API,
      load verified (3.2k req/s reads, 54 full logins/s, zero failures)
- [x] M7 monitoring & retention: `deploy/` Grafana + Prometheus compose stack with
      pre-provisioned auth dashboard; daily audit log purge (`auth.audit.*`)
- [x] M8 directory sync: WeCom + DingTalk org/user import via a config-activated
      provider SPI; admin trigger API; verified E2E against stub APIs
- [x] M9 sync console page (auth-web 目录同步) + provisioned Grafana alert rules
- [x] App access control (sys_app policies), operation audit, user center hardening
- [x] Device flow hardening (SAS 7.1.2 public-client gaps), password max-age policy
- [x] M10 HR-CSV directory provider + webhook notification contact points
- [x] W1 workflow module: embedded Flowable 8, approval flows bound to platform
      users/roles, dynamic forms, bpmn-js designer, AI pre-review node
- [x] W2 workflow advanced: parallel countersign (one-veto rule), delegation
      & transfer, SLA due dates + overdue webhooks, process analytics
      (cycle time per definition and per node)
- [x] M5c: DingTalk QR login, badge QR, per-device token policies, anomaly detection
      (new-location + brute-force rules → `login.anomaly` webhooks)
- [ ] M10b: LDAP provider (same SPI), email contact points

See the [development doc](docs/) (Chinese) for the full specification.

## License

TBD
