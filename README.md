# Performance tests (Gatling)

Maven project that load-tests **`application/`** — the `perf-test-app` Spring Boot service (default **http://127.0.0.1:9080**).

## Prerequisite

1. Start the app (from repo root’s `application` module), e.g. `mvn spring-boot:run` (listens on port **9080**).
2. Runs use **`127.0.0.1`** by default to reduce Windows **localhost / IPv6** issues; Gatling is started with **IPv4 preferred** (`pom.xml`).

## Run

```powershell
cd "c:\Hari Projects\Pipeline\performance"
mvn gatling:test
```

Point at another host/port: change the default in `PerfTestSimulation.java`, or add a line under `gatling-maven-plugin` → `jvmArgs` in `pom.xml`, for example:

```xml
<jvmArg>-DbaseUrl=http://your-host:9080</jvmArg>
```

Quick compile-only check:

```powershell
mvn test-compile
```

## Simulation

| Class | Behaviour |
|-------|-----------|
| `perf.gatling.PerfTestSimulation` | Ramps **12** virtual users over **18s**, each run: `GET /health` → `GET /api/hello` → `GET /api/work?iterations=2000` with short pauses. |

HTML report: **`target/gatling/`** (latest `perftestsimulation-* / index.html`).

## Troubleshooting

- **`BindException: Address already in use`** while Gatling runs: often fixed by using **`127.0.0.1`** (already the default) and **`-Djava.net.preferIPv4Stack=true`** (already in `pom.xml`). Avoid an accidental megaton scenario that exhausts ephemeral ports; restart the sample app if Tomcat was stressed.
- **`Connection refused`**: start `perf-test-app` first.

---

If perf tests live in **another repo**, Jenkins can checkout that repo instead; this folder stays as the in-repo Gatling project.
