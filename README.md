# TaskPilot Android

Native Android client for the TaskPilot portfolio project, built with Kotlin and Jetpack Compose. Android, future SwiftUI, and future React clients share [the public TaskPilot design system](TASKPILOT_DESIGN_SYSTEM.md) while following their platform conventions.

The app opens a dark introductory screen without making a network request. The networking foundation supports task CRUD and explicit connection checks; task management UI and a Settings screen are not implemented yet.

## Toolchain

The existing Android Studio toolchain is preserved. Compile and target SDK use API 36 because CI could not resolve the API 37 platform package:

| Component | Selected version |
| --- | --- |
| Application ID / namespace | `com.gurhangure.taskpilot` |
| Minimum SDK | 26 (Android 8.0) |
| Compile / target SDK | 36 (Android 16) |
| Android Gradle Plugin | 9.4.1 |
| Gradle wrapper | 9.6.0, distribution checksum pinned |
| Kotlin / Compose compiler plugin | 2.2.10 (AGP built-in Kotlin) |
| Compose BOM | 2026.02.01 |
| Gradle daemon JDK | 25, retained from the generated daemon criteria |
| Java source / target compatibility | 11 |
| SDK Build Tools | 36.0.0 (AGP default; installed explicitly in CI) |
| Activity Compose | 1.8.0 |
| Lifecycle runtime / ViewModel / Compose adapters | 2.6.1 declared baseline |
| Coroutines | 1.7.3 declared baseline |
| Core KTX | 1.10.1 |
| Retrofit / official kotlinx.serialization converter | 3.0.0 |
| OkHttp / MockWebServer | 4.12.0 |
| kotlinx.serialization JSON | 1.9.0 |
| Kotlin serialization compiler plugin | 2.2.10 |
| JUnit / AndroidX JUnit / Espresso | 4.13.2 / 1.1.5 / 3.5.1 |

Dependency versions are centralized in `gradle/libs.versions.toml`. Gradle can resolve higher transitive AndroidX versions required by Compose. Retrofit 3.0.0 uses OkHttp 4.12.0 and supports API 21+, below our API 26 minimum. Serialization 1.9.0 uses Kotlin 2.2.0 metadata and is paired with the existing Kotlin 2.2.10 compiler plugin. These compatible stable versions are pinned; no DI framework or database is introduced. The [AGP 9.4 compatibility table](https://developer.android.com/build/releases/agp-9-4-0-release-notes) specifies Gradle 9.6.0 and supports API 37. AGP's published 9.4.1 metadata declares Kotlin 2.2.10.

## Architecture

The foundation follows MVVM + Repository + unidirectional data flow in one app module:

```text
app/src/main/java/com/gurhangure/taskpilot/
├── TaskPilotApplication.kt     # Constructor-based networking composition
├── MainActivity.kt             # Lifecycle owner and Compose entry point
├── feature/home/
│   ├── HomeViewModel.kt        # Immutable StateFlow and initial UI state
│   └── HomeScreen.kt           # Stateless rendering and placeholder component
├── data/
│   ├── model/                 # Task, typed operation errors/results, content validation
│   ├── repository/            # TaskRepository, RemoteTaskRepository, connection state
│   └── remote/                # Retrofit API, DTOs, JSON/client factory, URL validation
└── ui/theme/                  # Dark colors, typography, spacing, and shapes
```

`MainActivity` obtains the screen ViewModel and collects its state with lifecycle awareness. State flows down to Compose; future user actions will flow back to the ViewModel. The screen makes no data-source calls. There are no task actions or navigation destinations yet.

`TaskPilotApplication` constructs one HTTP factory and an in-memory `ConnectionRepository`. The repository validates URL changes, creates a fresh endpoint-specific `RemoteTaskRepository`, and exposes immutable `StateFlow<ConnectionState>`: Unconfigured, Configured, Checking, Connected, or Failed. An explicit `testConnection()` uses GET /api/tasks, validates its response, and discards the data. Configuration alone does not claim connectivity. Cancelled probes propagate cancellation; obsolete probes cannot update state after reconfiguration.

`TaskRepository` exposes suspend list/get/create/update/delete methods with `ApiResult`. `RemoteTaskRepository` validates write content, maps HTTP/serialization failures, and converts DTOs into UI-independent `Task` snapshots with `UUID` and `Instant`. Retrofit owns route/body serialization and OkHttp owns transport. Future ViewModels receive repositories through constructors, without HTTP types or calls in composables. The home ViewModel remains unchanged until task UI work.

Configuration is in memory and reapplied from non-secret build properties after process restart. Reacquire `connections.taskRepository()` after `configure(url)`; already-started operations and previously held repository references retain their original endpoint. Invalid URL updates return InvalidUrl and preserve the last usable client. Runtime changes may use HTTPS or the one HTTP host permitted by the installed debug build; changing the HTTP host requires a rebuild.

The fixed dark theme disables dynamic colors and theme switching. Shared tokens use dp/sp, native typography, semantic headings, system insets, a bounded content width, and vertical scrolling for large fonts. Platform launch and system-bar styling are dark too.

## Development setup

1. Use an Android Studio stable release that supports AGP 9.4, with JDK 25 available for the generated Gradle daemon criteria.
2. Clone this repository and open its root in Android Studio. Preserve the checked-in Gradle wrapper and version catalog.
3. Install Android SDK Platform 36 and Build Tools 36.0.0 through SDK Manager. Accept any required licenses yourself.
4. Let Gradle sync. Android Studio manages the ignored `local.properties` SDK location.
5. Run the `app` configuration on an API 26+ device or emulator. No API server is needed.

Build from the project root (`gradlew.bat` on Windows):

```sh
./gradlew :app:assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. It uses standard debug signing; no release keystore is needed. No public release or store publishing is configured.

## Tests and CI

Automated tests are authored for GitHub Actions and are **not run locally by default**. The networking changes have not been tested locally; their CI result is pending/unverified.

| Level | Foundation coverage | Execution |
| --- | --- | --- |
| JVM unit | URL validation/normalization, local HTTP policy, connection state/reconfiguration/cancellation, initial ViewModel state | `:app:testDebugUnitTest` in CI |
| Repository/API integration | Real Retrofit/OkHttp/JSON against MockWebServer: CRUD routes, null/false serialization, UTC timestamps, validation, 404/HTTP errors, malformed bodies, DNS failure, timeout, cancellation, endpoint changes | `:app:testDebugUnitTest` includes the `integration` package |
| Compose component | Standalone home screen and reachable guidance at 2x font scale | Emulator `component` package |
| E2E | Real activity launch and recreation without backend configuration | Emulator `e2e` package; startup smoke coverage only |

The existing template arithmetic and package-name examples are replaced with behavior checks. Full create/edit/complete/reopen/delete E2E journeys and error/loading/empty component coverage must be added alongside those features. The startup smoke test is not evidence of CRUD coverage.

[Android CI](.github/workflows/android.yml) runs on pushes and pull requests to `main`, and supports manual dispatch. It provides:

- JVM unit and MockWebServer integration tests, Android Lint, compilation, and debug APK generation in one job.
- Separate component and startup E2E emulator jobs, selected by instrumentation package on API 35.
- Gradle caching, read-only repository permissions, bounded timeouts, and cancellation of superseded runs.
- A 14-day debug APK artifact after the build job succeeds and 7-day diagnostic reports, including on failure.

An APK artifact alone does not mean emulator jobs passed. Review every job before treating a build as verified. API 26 and API 36 runtime behavior and physical devices are not covered by the initial API 35 emulator configuration.

## Backend and networking

Contracts were inspected in the [backend controllers](https://github.com/gurhangure/taskpilot-api/blob/main/src/TaskPilot.Api/Controllers/TasksController.cs), request models, TaskResult, validation service, UTC persistence mapping, and endpoint tests. The backend repository is maintained independently.

| Operation | Request | Success |
| --- | --- | --- |
| GET /api/tasks | None | 200, task array |
| GET /api/tasks/{id} | GUID path parameter | 200, task |
| POST /api/tasks | title, nullable description | 201, created task |
| PUT /api/tasks/{id} | title, nullable description, isCompleted | 200, updated task |
| DELETE /api/tasks/{id} | GUID path parameter | 204, no body |

Responses contain `id`, `title`, nullable `description`, `isCompleted`, and `createdAtUtc`. Required response fields have no fallback defaults. UTC ISO-8601 timestamps preserve fractional seconds in `Instant`; malformed identities/timestamps/content become InvalidResponse. Additive JSON fields are ignored. Requests explicitly encode null descriptions and false completion so PUT can clear and reopen tasks. POST never sends server-owned identity/completion/time fields.

Title validation matches the backend's .NET whitespace rules and 1–200 UTF-16 code-unit limit; description is optional with a 2,000-code-unit limit. Text is preserved without trimming. HTTP 400 maps ValidationProblemDetails field errors (or an empty field map for malformed/nonstandard 400 bodies); 404 maps NotFound consistently for get/update/delete; other HTTP errors retain their status code. Invalid response bodies, connection failures, and timeouts have separate errors. Transport exception details and raw error bodies are not exposed or logged. Cancellation is rethrown.

Timeouts are 10 seconds to connect, 15 seconds to read/write, and 30 seconds for a whole call. Automatic connection retries and redirects are disabled. No authentication, logging interceptors, or custom TLS trust are installed.

### Configure an endpoint

By default, no URL is selected and HTTP is blocked in both build types. Build properties are **not secret storage**: their values are embedded in the APK. Do not include credentials, tokens, queries, or fragments in an endpoint.

For an HTTPS server:

```sh
./gradlew :app:assembleDebug -Ptaskpilot.apiBaseUrl=https://example.test/
```

To explicitly opt a debug APK into local HTTP for the Android emulator:

```sh
./gradlew :app:assembleDebug -Ptaskpilot.debugHttpHost=10.0.2.2
```

This selects `http://10.0.2.2:5080/`, matching the backend README's local port example. For another port, add `-Ptaskpilot.apiBaseUrl=http://10.0.2.2:5087/`. The backend must already be running separately; no server is started by the app or build.

For a physical device, replace `HOST_LAN_IP` below with the host's private IPv4 address:

```sh
./gradlew :app:assembleDebug \
  -Ptaskpilot.debugHttpHost=HOST_LAN_IP \
  -Ptaskpilot.apiBaseUrl=http://HOST_LAN_IP:5080/
```

Both properties may instead be supplied as Gradle project properties in Android Studio's Gradle configuration. No source-code edit is required. URLs are validated at application configuration time and normalized with a trailing slash; deployment path prefixes are preserved. Invalid configuration becomes Failed(InvalidUrl) rather than crashing startup. No Settings screen or on-screen connection status is added in this increment; future presentation code consumes `TaskPilotApplication.connections` through constructor injection.

`10.0.2.2` is specific to the Android emulator and maps to the development host's loopback interface. Physical devices need a reachable private LAN address, a backend listener explicitly bound to that address, and trusted-network connectivity. Device `localhost` refers to the device itself. Follow the backend README for server setup and narrow firewall rules; do not expose this unauthenticated demo to the internet.

### Network security

The main manifest grants INTERNET and disables cleartext. Debug builds generate a network-security resource with cleartext disabled by default and, only when explicitly configured, one exact IPv4 domain exception with no subdomains. Allowed development hosts are RFC1918 private addresses or 127.0.0.1; the emulator address is in the private range. The exception is host-scoped and permits that host's ports, while the configured URL selects the actual port. Release builds do not include this debug manifest/resource, always have an empty allowed-HTTP-host setting, and reject HTTP URLs even when debug properties were supplied.

The current local API provides neither HTTPS nor authentication. HTTP traffic and task contents can be observed or changed on the network; use disposable demo data on a trusted LAN. HTTPS uses normal platform certificate and hostname verification; development certificates must be properly trusted rather than bypassed. Physical-device connectivity and live backend integration have **not** been verified. Build-generated network policies and the new code await CI verification.

## Next increments

1. Wire task repositories into screen ViewModels with constructor injection.
2. Add loading/empty/error states, task counts, search, and status filters with ViewModel and component coverage.
3. Implement CRUD and completion workflows, plus deterministic emulator journeys.
4. Add persistent connection Settings and verify live backend and physical-device connectivity.

No login, reminders, categories, realtime sync, offline writes, light mode, or theme toggle is planned for v1.

`AGENTS.md`, numbered `prompts/`, IDE state, SDK paths, and signing files stay ignored and untracked. The public design system remains tracked.
