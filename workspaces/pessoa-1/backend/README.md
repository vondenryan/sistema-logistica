# Pessoa 1 backend

Spring Boot 3.5.7 (published stable release, resolved and compiled with Java 21); PostgreSQL and Flyway. Maven 3.9.11 is pinned by `./mvnw`, which downloads the distribution from Maven Central and verifies SHA-512. The launcher uses Eclipse's Java 21 compiler, so it works with a Java 21 runtime as well as a JDK. `curl`, `tar`, `sha512sum` and Python 3 are needed on first use. The launcher writes its Maven installation, settings and repository under `.cache/`; set `MAVEN_LAUNCHER_CACHE` to an absolute writable directory to share a cache. It derives proxy settings from standard HTTP(S)_PROXY and NO_PROXY variables and does not disable TLS verification.

Start a PostgreSQL database at localhost:5433 with database and user `logistics`, then supply secrets via environment variables and start:

```sh
export DB_PASSWORD='<your database password>'
export JWT_SECRET='<at least 32 bytes of random secret>'
export DEMO_PASSWORD='<your chosen demo password>'
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The server listens on port 8080. `DB_URL` and `DB_USER` can override the defaults. Passwords and signing keys are never seeded from the repository. The dev seed updates BCrypt hashes from `DEMO_PASSWORD` at each startup. Demo identities are `admin@example.invalid`, `operator@example.invalid`, `store@example.invalid`, `driver@example.invalid`, and `other-store@example.invalid`. The first store is `22222222-2222-4222-8222-222222222222`, origin `11111111-1111-4111-8111-111111111111`; the second store is `22222222-2222-4222-8222-222222222223`, distribution-center origin `11111111-1111-4111-8111-111111111112`. Inactive origin `11111111-1111-4111-8111-111111111113` exercises the rejection path. Driver and vehicle IDs are `55555555-5555-4555-8555-555555555555` and `66666666-6666-4666-8666-666666666666`.

Run offline application tests (dependencies must have been downloaded once):

```sh
./mvnw test
```

The tests use H2 PostgreSQL mode and in-process fixtures; no other service is required. The PostgreSQL driver and Flyway PostgreSQL support are used by the dev server. API endpoints use `/api/v1`; order update is `PATCH /api/v1/orders/{id}` with the complete creation payload and is allowed only while DRAFT. Draft cancellation requires a reason; confirmed cancellation is blocked pending the business decision. Money and weights are decimal strings. JWT expires in one hour; login has a per-process bounded IP limit.

Confirmation commits order status, a stable event ID and a unique simulated payment in one local database transaction. Delivery and notification calls happen after commit. Failures return 503; retrying confirmation or the 30-second reconciliation task retries unfinished work with the original event. No distributed atomicity is assumed.

**Dev fake lifecycle:** fixtures keep deliveries, assignments and notifications in process memory. Their state is lost on server restart. In dev only, the orders-owned startup listener resets delivery/notification completion flags for confirmed orders and replays the original durable events into the new fakes. Assignments remain process-local and are not restored; routing is outside Sprint 1. These fakes are not production persistence. Production/integration must supply real adapters; mixing `dev`/`test` with `prod`/`integration` fails startup. The explicit `FakeCurrentActorPort` is never registered automatically and cannot bypass JWT authentication.

Freight quoting and routing algorithms remain Sprint 2 work. There is no configured tariff or invented distance. Document fake returns a receipt only when its order has a freight amount. The `billing` and `routing` Flyway schemas are scaffolding for future work; fake carrier/delivery/notification state is not a replacement for the owners' modules.
