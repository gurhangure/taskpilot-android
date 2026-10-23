# TaskPilot Android

Native Android client for the TaskPilot portfolio project, built with Kotlin and Jetpack Compose. Android, future SwiftUI, and future React clients share [the public TaskPilot design system](TASKPILOT_DESIGN_SYSTEM.md) while following their platform conventions.

This foundation opens a dark introductory screen with a future task-list placeholder. It starts without a backend, network permission, or configured endpoint. Task CRUD and connection settings are not implemented yet.

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
| JUnit / AndroidX JUnit / Espresso | 4.13.2 / 1.1.5 / 3.5.1 |

Dependency versions are centralized in `gradle/libs.versions.toml`. Gradle can resolve higher transitive AndroidX versions required by Compose. Lifecycle Compose adapters and coroutines are explicit dependencies; no DI framework, database, or HTTP library is introduced. The [AGP 9.4 compatibility table](https://developer.android.com/build/releases/agp-9-4-0-release-notes) specifies Gradle 9.6.0 and supports API 37. AGP's published 9.4.1 metadata declares Kotlin 2.2.10.

## Architecture

The foundation follows MVVM + Repository + unidirectional data flow in one app module:

```text
app/src/main/java/com/gurhangure/taskpilot/
├── MainActivity.kt             # Lifecycle owner and Compose entry point
├── feature/home/
│   ├── HomeViewModel.kt        # Immutable StateFlow and initial UI state
│   └── HomeScreen.kt           # Stateless rendering and placeholder component
├── data/
│   ├── repository/ConnectionRepository.kt
│   └── remote/ApiConfiguration.kt
└── ui/theme/                  # Dark colors, typography, spacing, and shapes
```

`MainActivity` obtains the screen ViewModel and collects its state with lifecycle awareness. State flows down to Compose; future user actions will flow back to the ViewModel. The screen makes no data-source calls. There are no task actions or navigation destinations yet.

`ConnectionRepository` defines a small read boundary with a constructor-injected, in-memory implementation. It defaults to no endpoint. `ApiConfiguration` validates HTTPS URLs and rejects embedded credentials, queries, fragments, and invalid ports without performing I/O. This boundary is intentionally not connected to the home screen until Settings/API work needs it. It does not persist configuration or test connectivity. No task models or pretend loaded task data are introduced.

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

Automated tests are authored for GitHub Actions and are **not run locally by default**. No build or test execution was verified during this foundation implementation; the first CI run remains pending/unverified.

| Level | Foundation coverage | Execution |
| --- | --- | --- |
| JVM unit | HTTPS endpoint validation, explicit configuration, initial ViewModel state | `:app:testDebugUnitTest` in CI |
| Repository/API integration | Deferred until a real HTTP/serialization boundary exists; use a controlled server and isolated fixtures | No empty job or live API dependency |
| Compose component | Standalone home screen and reachable guidance at 2x font scale | Emulator `component` package |
| E2E | Real activity launch and recreation without backend configuration | Emulator `e2e` package; startup smoke coverage only |

The existing template arithmetic and package-name examples are replaced with behavior checks. Full create/edit/complete/reopen/delete E2E journeys and error/loading/empty component coverage must be added alongside those features. The startup smoke test is not evidence of CRUD coverage.

[Android CI](.github/workflows/android.yml) runs on pushes and pull requests to `main`, and supports manual dispatch. It provides:

- JVM tests, Android Lint, compilation, and debug APK generation in one job.
- Separate component and startup E2E emulator jobs, selected by instrumentation package on API 35.
- Gradle caching, read-only repository permissions, bounded timeouts, and cancellation of superseded runs.
- A 14-day debug APK artifact after the build job succeeds and 7-day diagnostic reports, including on failure.

An APK artifact alone does not mean emulator jobs passed. Review every job before treating a build as verified. API 26 and API 36 runtime behavior and physical devices are not covered by the initial API 35 emulator configuration.

## Backend and networking

The independently maintained [TaskPilot ASP.NET Core API](https://github.com/gurhangure/taskpilot-api) documents task CRUD under `/api/tasks`, validation responses, and local deployment. Its README was reviewed for this foundation; no HTTP client, DTOs, or requests are implemented. Recheck its current OpenAPI and request/response contracts before implementing those layers.

Future Settings will accept an explicitly configured base URL and provide a connection test. Android emulator `10.0.2.2` refers to the development host's loopback interface. A physical device instead needs the host's reachable private LAN address and an explicitly configured listener; device `localhost` refers to the device itself.

The backend's current local HTTP setup is not yet supported by this client foundation. Cleartext is explicitly disabled and there is no `INTERNET` permission. A future local-demo HTTP option must be explicit, debug-only, and narrowly scoped with network-security configuration. HTTPS must retain normal certificate validation; changing a URL scheme does not establish certificate trust. Local-network permission requirements for target SDK 36 and physical-device connectivity must be verified during networking work. Do not expose the unauthenticated demo API to the public internet.

## Next increments

1. Confirm API contracts, add an HTTP client and task repository, and activate controlled integration tests.
2. Add loading/empty/error states, task counts, search, and status filters with ViewModel and component coverage.
3. Implement CRUD and completion workflows, plus deterministic emulator journeys.
4. Add persistent connection Settings, explicit local-demo security configuration, and device connectivity verification.

No login, reminders, categories, realtime sync, offline writes, light mode, or theme toggle is planned for v1.

`AGENTS.md`, numbered `prompts/`, IDE state, SDK paths, and signing files stay ignored and untracked. The public design system remains tracked.
