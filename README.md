# TaskPilot Android

Native Kotlin/Jetpack Compose client for the [TaskPilot API](https://github.com/gurhangure/taskpilot-api). The dark Task List shows Total, Active, and Completed counts, read-only task cards, All / Active / Completed filters, Refresh, and Retry. Connection Settings stores a server URL on the device and applies changes immediately.

## Quick Start

**Android Studio users:** install a stable Android Studio version supporting AGP 9.4, JDK 25, Android SDK Platform 36, and Build Tools 36.0.0. Git and network access are needed for the first dependency sync. Accept SDK licenses yourself.

1. Clone `https://github.com/gurhangure/taskpilot-android.git`.
2. In Android Studio choose **Open** and select the repository root. Allow **Gradle Sync** to finish.
3. Select an API 26+ emulator/device and **Run app**. No source, Gradle, XML, or build-property edits are required.
4. On first launch choose **Connect to Server**. Start the backend separately using the commands below.
5. Enter the server URL. On an emulator, **Use emulator address** suggests `http://10.0.2.2:5087/`; it never selects a server automatically. Review and acknowledge the HTTP warning when using it.
6. Choose **Save & Connect**, then **Back to Task List**. A successful verification displays **Connected**; the list loads automatically. An empty server displays **No tasks yet**. Other outcomes show connection or API recovery guidance.

The server URL is restored on later launches. Use **Connection Settings** above the list to edit it. **Test Connection** verifies the draft without saving or selecting it. **Save & Connect** remembers a valid address even if its server is temporarily unavailable. Failed validation or disk writes preserve the previous saved choice. A failed connection never deletes settings.

**APK users:** use the build-artifact instructions below when an APK for the desired commit is available. Download/extract `taskpilot-debug-apk`, transfer `app-debug.apk` to the device, allow installation from that specific installer if Android requests it, and open TaskPilot. The same in-app setup applies. A physical device needs a supported HTTPS server; the emulator suggestion does not refer to the computer from a phone.

## Backend setup

Prerequisites: Git and the **.NET SDK** matching the backend's `global.json` (currently 10.0.401 or a compatible 10.0.4xx patch). The runtime alone is insufficient. NuGet access is required for the first restore.

From a directory where you want a separate backend checkout, on macOS/Linux:

```bash
git clone https://github.com/gurhangure/taskpilot-api.git
cd taskpilot-api
dotnet run --project src/TaskPilot.Api --urls http://127.0.0.1:5087
```

These commands were checked against the current project path, target framework, controller contracts, and startup initializer. They were not executed against a live server during this Android change. `dotnet run` restores/builds the project as needed. The backend applies pending SQLite migrations automatically before accepting requests, in both source and packaged modes. No EF Core migration command or separate database server is required. Do not reset a developer database if initialization fails; follow the backend README's troubleshooting.

On Windows, use the same three commands in PowerShell with the matching .NET SDK. For optional diagnostics use `curl.exe` rather than PowerShell's `curl` alias. The commands require no bash environment-variable syntax. The source process creates/uses its configured SQLite database; see the backend README for paths and overrides.

Keep the terminal running. The expected listener is `http://127.0.0.1:5087`. An optional host-side check is:

```bash
curl http://127.0.0.1:5087/api/health
```

The documented response is HTTP 200 with `{"status":"ok"}`. The Android connection check also validates GET `/api/tasks`, including its task schema, so an unrelated HTTP success page is rejected. Verification is contract checking, not cryptographic server authentication; this demo has no authentication.

## Emulator and physical-device connections

- **Android emulator:** explicitly select `http://10.0.2.2:5087/` in Settings. `10.0.2.2` is the emulator alias for the computer's loopback listener. The local backend must be running on port 5087.
- **Physical device:** enter an HTTPS endpoint with a certificate trusted by Android and valid for its hostname. If hosted on your computer, it needs a listener reachable on the trusted LAN, suitable HTTPS configuration, device/host connectivity, and narrowly scoped firewall permission. The local HTTP-only command above does not provide that HTTPS endpoint. Changing `http` to `https` alone does not configure TLS.
- **Device `localhost`/`127.0.0.1`:** refers to the Android device itself, not the computer. Never use a wildcard binding such as `0.0.0.0` as the client address.

Stock APKs cannot add arbitrary private-LAN HTTP exceptions at runtime. Android network-security domain exceptions are packaged resources, not editable URL preferences. Entering an arbitrary LAN IP in Settings therefore does not authorize cleartext to that IP. Use supported HTTPS instead. Physical-device connectivity, including the optional custom debug method below, has not been verified.

## HTTP policy and optional development configuration

Both debug and release builds retain normal HTTPS certificate/hostname validation and block cleartext by default. They include one deliberate demo exception: exact host **10.0.2.2**, without subdomains. This lets an emulator user configure the local backend in-app without rebuilding. The user must acknowledge the unencrypted-HTTP warning before Settings sends HTTP requests. It is never used automatically on an emulator or physical device.

HTTP has no encryption or server authentication. Anyone with access to the network/backend can potentially observe or modify demo tasks. Use disposable data on a trusted network. No credentials, API keys, passwords, tokens, URL queries, fragments, or embedded user information are accepted or stored. Do not expose the unauthenticated backend to the public internet or bypass certificate validation.

| Mode | HTTPS | Emulator HTTP `10.0.2.2` | Other private-LAN HTTP |
| --- | --- | --- | --- |
| Default debug APK | Normal trust validation | Explicit in-app choice and acknowledgement | Blocked |
| Release build | Normal trust validation | Explicit, documented demo exception and acknowledgement | Blocked |
| Custom debug APK | Normal trust validation | Same exception | One exact private IPv4 host selected at build time; no subdomains |

Existing non-secret build properties remain supported for development and automated fixtures, but are optional. **Saved user settings always win over a build fallback**. A build fallback is not written to DataStore until the user saves it.

```sh
# Optional emulator fallback; unnecessary for normal in-app setup.
./gradlew :app:assembleDebug \
  -Ptaskpilot.debugHttpHost=10.0.2.2 \
  -Ptaskpilot.apiBaseUrl=http://10.0.2.2:5087/

# Optional custom debug build: replace HOST_LAN_IP with an actual private IPv4 address.
./gradlew :app:assembleDebug \
  -Ptaskpilot.debugHttpHost=HOST_LAN_IP \
  -Ptaskpilot.apiBaseUrl=http://HOST_LAN_IP:5087/
```

The custom debug policy accepts RFC1918 private IPv4 addresses or `127.0.0.1`, and permits cleartext only for that exact host plus the emulator exception. The exception covers ports on that host; the URL chooses the actual port. Such a physical-device debug setup also needs the backend explicitly listening on that private address, the device on a reachable trusted network, and narrow firewall access. No host binding, firewall, or backend changes are made by this Android app. Release builds ignore the custom debug-host exception. For HTTPS, an optional `-Ptaskpilot.apiBaseUrl=https://server.example/` fallback is supported. Build-property values are embedded in the APK and must never contain secrets.

## Architecture and data flow

The app follows MVVM + Repository + unidirectional data flow with constructor injection, coroutines, and immutable StateFlow snapshots. Two screens use saveable Activity-level navigation and native Back handling; no navigation framework is introduced.

```text
MainActivity                         Screen navigation and lifecycle-aware state collection
feature/home/                        Task list state, ViewModel, counts, filters, and components
feature/connection/                  Editable URL, consent, save/test actions, verification feedback
TaskPilotApplication                 One process-scoped HTTP factory and one DataStore instance
 data/settings/                      EndpointSettings contract and Preferences DataStore adapter
 data/repository/                    Active/persistent connection selection and task operations
 data/remote/                        URL policy, Retrofit routes/DTOs, strict JSON, shared OkHttp
 data/model/                         Task snapshots, validation, and typed errors/results
ui/theme/ and ui/components/          Shared dark design and error guidance
```

Startup waits for asynchronous Preferences DataStore restoration before requesting tasks. With no saved/default endpoint it shows a friendly connection-required state and makes no invalid request. A saved or explicitly supplied endpoint triggers automatic list loading. Restoration errors are displayed rather than silently switching to a different build server. Settings stores only the normalized URL, using atomic DataStore edits. It is loaded across process restarts; no tasks are stored offline.

A save validates the URL, persists it, and then publishes the new endpoint. The Home ViewModel cancels older list requests, clears the previous server's data, reacquires the task repository, and loads the new list. Endpoint and request-version checks reject stale results. Successful Save & Connect verification also recovers an earlier list error, waiting for any active load before retrying; healthy data is not reloaded for an unchanged URL. Valid same-URL configuration reuses its repository. Retrofit services share one OkHttp client. Draft tests do not change the active endpoint; cancellation and version checks prevent superseded probes from replacing newer feedback. Leaving Settings cancels its pending verification.

The task list retains global counts, local filters, stable LazyColumn task-ID keys, manual refresh, and typed recovery messages. Refresh preserves the last loaded collection and displays a warning on failure. This is transient screen state, not an offline cache. Activity recreation retains ViewModels and navigation selection.

The API supports GET/POST `/api/tasks` and GET/PUT/DELETE `/api/tasks/{id}`; this UI only lists tasks. `RemoteTaskRepository` validates/marshals DTOs into UUID/Instant snapshots, preserves null/false write semantics, maps HTTP/transport/data errors, and rethrows cancellation. Connection verification requires HTTP 200 plus `status == "ok"` from `/api/health`, followed by a valid task array from `/api/tasks`. Health checks alone do not prove database availability; the task request verifies that boundary as well. Deployment path prefixes and trailing-slash normalization are preserved.

Timeouts remain 10 seconds to connect, 15 seconds to read/write, and 30 seconds per HTTP call. A two-request verification can take up to two call deadlines. Automatic connection retries and redirects remain disabled. No body-logging interceptor or custom TLS trust is installed.

## Toolchain and building

| Component | Version |
| --- | --- |
| Application ID | `com.gurhangure.taskpilot` |
| Minimum SDK / compile and target SDK | 26 / 36 |
| AGP / Gradle wrapper | 9.4.1 / 9.6.0 |
| Kotlin / Compose compiler / serialization plugin | 2.2.10 |
| Compose BOM / Material 3 | 2026.02.01 / BOM-managed |
| Gradle daemon JDK / Java source target | 25 / 11 |
| SDK Build Tools | 36.0.0 |
| Lifecycle / Activity Compose / Core KTX declared baseline | 2.6.1 / 1.8.0 / 1.10.1 |
| Coroutines declared baseline | 1.7.3 |
| Retrofit and official serialization converter | 3.0.0 |
| OkHttp / MockWebServer | 4.12.0 |
| kotlinx.serialization JSON | 1.9.0 |
| Preferences DataStore | 1.1.7 |
| JUnit / AndroidX JUnit / Espresso | 4.13.2 / 1.1.5 / 3.5.1 |

Versions are centralized in `gradle/libs.versions.toml`. DataStore is the only new library family for Settings. Its published metadata supports the existing Kotlin/SDK baseline; unrelated toolchain versions are preserved. Gradle may resolve higher compatible transitive AndroidX/coroutine versions. Follow [TASKPILOT_DESIGN_SYSTEM.md](TASKPILOT_DESIGN_SYSTEM.md); dark-only, no dynamic theme switching.

```sh
./gradlew :app:assembleDebug
```

Use `gradlew.bat` on Windows. The installable debug-signed APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. No release keystore or store publishing is configured. Android Studio manages the ignored local SDK path. No local build or automated test execution is claimed for this increment.

## GitHub Actions and APK artifacts

[Android CI](https://github.com/gurhangure/taskpilot-android/actions/workflows/android.yml) runs JVM tests, Android Lint, compilation, debug APK upload, and separate API 35 emulator component and E2E suites. Gradle caching, timeouts, read-only repository permissions, and existing jobs remain intact. JVM tests include MockWebServer and temporary-file DataStore integration tests; they require neither a developer backend nor a real database. Automated tests are authored for CI and were not run locally.

For the desired commit, open its Actions run and check **all jobs**. When available, download **taskpilot-debug-apk** under **Artifacts**; extract it to find `app-debug.apk`. GitHub sign-in may be required. APK retention is 14 days and reports are retained for 7 days. A build artifact by itself does not prove emulator success. The Settings increment is pending/unverified until its own CI completes.

A [verified earlier Task List build](https://github.com/gurhangure/taskpilot-android/actions/runs/40017734094) has a [debug APK artifact](https://github.com/gurhangure/taskpilot-android/actions/runs/40017734094/artifacts/12528549916). Its run reported success when inspected, but it predates Connection Settings and does **not** provide the onboarding described here. Do not use that older APK to evaluate this increment; use source or a subsequent verified artifact containing Settings. Artifacts expire; no permanent release/download link is published.

| Level | Coverage |
| --- | --- |
| JVM unit | Fresh/restored settings, precedence, URL normalization/policy, save/failure, connection feedback, cancellation/stale results, live endpoint switching in the Home ViewModel, counts/filters/retry/refresh |
| Integration | Real Retrofit/OkHttp/JSON against MockWebServer, health and task contracts, unrelated/malformed servers, HTTP errors, timeouts/cancellation, plus Preferences DataStore save/reopen on an isolated file |
| Compose components | Task List loading/empty/populated/error states, first-launch Connect action, Settings URL/actions/consent/status, and large-font guidance |
| Emulator E2E | Real first-launch navigation into Settings, invalid URL handling without a network call, return to Task List, and Activity recreation |

Live API integration, physical devices, API 26/36 runtime coverage, and task mutation E2E journeys remain unverified or future work. Existing repository and task-list tests remain enabled.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Fresh install shows Connect to Server | Expected: choose the server inside the app; no build edits are needed |
| Server unreachable / timeout | Start the API separately; check port, scheme, host binding, VPN, host sleep, and trusted-network reachability |
| Phone cannot use the emulator URL | `10.0.2.2` maps to the host only inside an Android emulator; use supported HTTPS for the phone |
| Private-LAN HTTP URL is rejected | Stock builds deliberately block it; use HTTPS or the explicitly configured custom debug method |
| TLS error | Use a valid certificate trusted by the device and matching the hostname; never disable verification |
| HTTP 404 | Check the base URL and any deployment path prefix; health and tasks routes must both exist |
| Incompatible API response | Use the actual TaskPilot backend; generic HTML/HTTP success is not sufficient |
| Saved URL differs from a new build fallback | Expected: user settings take precedence; edit and save in Settings |
| Verification fails after Save & Connect | The URL remains saved; fix/start the server, test again, or return and retry the list |
| Settings cannot be read/saved | Check available device storage; prior stored data is not deliberately erased |
| SQLite migration startup fails | Read the backend initialization error and README; do not delete/reset the database |
| Port 5087 already in use | Select another port in the backend command and enter the same port in Settings |

## Current limitations

No task creation/editing/deletion/completion UI, title search, authentication, offline cache, background sync, QR pairing, or light theme. The API has no authentication or user isolation. Connection verification checks documented contracts and cannot authenticate an adversarial server. URL configuration is persistent, but task data and draft feedback are not stored across process death. No public release, production server, or store signing is configured.

`AGENTS.md`, numbered `prompts/`, SDK paths, IDE state, credentials, and signing files remain ignored/untracked. The public design specification remains tracked.
