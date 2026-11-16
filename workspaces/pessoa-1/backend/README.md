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

Freight quotes are available for confirmed orders. Configure `FREIGHT_TARIFF_PER_KM` as a non-negative BRL/km decimal with at most two decimal places; no tariff is supplied by default. An absent or invalid tariff returns `PRECONDITION_FAILED` when creating a quote, but existing immutable quotes remain readable. The first quote snapshots distance, tariff and the rounded amount; concurrent requests return the same snapshot. Draft quoting is intentionally unsupported because draft addresses can change.

Routing defaults to OSRM at `http://localhost:5000`; set `OSRM_BASE_URL` to the deployed provider URL. HTTP(S) proxy environment variables and `NO_PROXY` are honored; local loopback bypasses the proxy. Network and service unavailability return 503; malformed upstream responses return 502. Neither case invents road distances. For offline development and tests only, select `ROUTING_PROVIDER=fake` with the dev or test profile; its deterministic distance is explicitly reported with `source: FAKE`. Manual coordinates remain available; `GeocodingProvider` is a port and no geocoder has been selected.

Routes accept confirmed orders with one origin and store, at most ten unique stops. Suggestions use nearest-neighbor road distances and do not persist. Creation stores the submitted order. The route stop uniqueness constraint prevents concurrent active reuse; because route completion/cancellation is not implemented, it currently also blocks reuse of historical stops. Implement release as part of a future approved terminal lifecycle. Assignment stores the fleet result and retries delivery assignments with stable event IDs. Starting a route requires assignment and completed reconciliation. The v1 delivery command port has no start/in-transit operation, so starting only changes the route; delivery transitions remain with Pessoa 3.

Dev fixtures are process-local and not production implementations. On dev startup confirmed orders replay first, then assigned and in-progress routes restore fake fleet reservations and delivery assignments. Startup fails if restoration fails; the fake provides one driver and one vehicle, so incompatible persisted active routes cannot be silently restored. Production/integration requires the real external module adapters and must not enable dev/test fakes.

## Container and integration boundary

Build the executable JAR on the host with the Maven dependencies already cached, then build from this backend directory:

```sh
./mvnw -o test
./mvnw -o package
docker build -t pessoa1-backend:local .
```

The image copies only `target/backend-1.0.0.jar`, uses the Java 21 JRE, runs as UID/GID 10001 and listens on container port 8080. Docker may need to pull the base image; the Docker build itself does not run Maven or fetch Java dependencies. Bind the container port as appropriate (for example `-p 8080:8080`) and provide database connectivity (`DB_URL`, `DB_USER`, `DB_PASSWORD`) and `JWT_SECRET` at runtime through the deployment's secret mechanism. Container `localhost` refers to the container itself; use reachable database and OSRM addresses. Configure `OSRM_BASE_URL` and the operational `FREIGHT_TARIFF_PER_KM` as needed. Never bake credentials into the image. The `dev` profile additionally requires `DEMO_PASSWORD` for the seed.

`integration` and `prod` cannot be combined with `dev`/`test`, and reject `ROUTING_PROVIDER=fake`. They currently fail at startup with a missing real port provider diagnostic: P2/P3 have not delivered their modules. This image is a P1 deployment prerequisite, not a working integrated or production system. The guard inventories all seven external ports, but bean presence does not prove a real or compatible implementation. Integration also requires real identity provisioning, coordinated Flyway histories and real-provider contract, security and end-to-end testing; see INTEGRATION.md.
