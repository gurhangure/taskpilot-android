# TaskPilot Android

Native Kotlin/Jetpack Compose client for the [TaskPilot API](https://github.com/gurhangure/taskpilot-api), built with Material 3 and MVVM + Repository + unidirectional data flow.

Implemented features:

- Create, view, edit, delete, complete, and reopen tasks.
- Local title/description search, All / Active / Completed filters, and task statistics.
- Connection Settings with Test Connection, Save & Connect, and Preferences DataStore persistence.
- Dark-only, accessible Compose UI with loading, empty, error, Retry, and manual Refresh states.

## Quick Start: run from source

Install Git and an Android Studio version compatible with **AGP 9.4.1**, **JDK 25**, Android SDK Platform **36**, and Build Tools **36.0.0**. Use Android Studio's SDK Manager to install missing SDK components and accept their licenses yourself. Network access is needed for the first Gradle dependency sync.

```bash
git clone https://github.com/gurhangure/taskpilot-android.git
cd taskpilot-android
```

1. In Android Studio choose **Open** and select this repository root, containing `settings.gradle.kts`.
2. Allow **Gradle Sync** to finish. Use JDK 25 for the Gradle daemon; SDK/toolchain paths are managed by Android Studio and the existing Gradle configuration.
3. In Device Manager create/start an API 26+ emulator, or select a connected supported device. CI uses an API 35 emulator.
4. Select the **app** run configuration and press **Run ▶**. A fresh install shows **Connect to Server**; configure it inside the app.

No source-file, Gradle-property, or manually edited `local.properties` changes are required. Optional custom development build properties are documented separately below and are not needed for normal operation.

Next, start the separate backend and connect the emulator using the steps below.

## Backend setup

**Source prerequisites:** Git and the **.NET SDK** matching the backend's `global.json` (currently 10.0.401 or a compatible 10.0.4xx patch). The runtime alone is insufficient for source builds. NuGet access is required for the first restore. A [prebuilt backend](#use-a-prebuilt-backend-instead-of-installing-the-net-sdk) is an optional alternative.

From a directory where you want a separate backend checkout, on macOS/Linux:

```bash
git clone https://github.com/gurhangure/taskpilot-api.git
cd taskpilot-api
dotnet run --project src/TaskPilot.Api --urls http://127.0.0.1:5087
```

`dotnet run` restores/builds the project as needed. The backend applies pending SQLite migrations automatically before accepting requests, in both source and packaged modes. No EF Core migration command or separate database server is required. Do not reset a developer database if initialization fails; follow the backend README's troubleshooting.

On Windows, use the same three commands in PowerShell with the matching .NET SDK. For optional diagnostics use `curl.exe` rather than PowerShell's `curl` alias. The commands require no bash environment-variable syntax. The source process creates/uses its configured SQLite database; see the backend README for paths and overrides.

Keep the terminal running. The expected listener is `http://127.0.0.1:5087`. An optional host-side check is:

```bash
curl http://127.0.0.1:5087/api/health
```

The documented response is HTTP 200 with `{"status":"ok"}`. The Android connection check also validates GET `/api/tasks`, including its task schema, so an unrelated HTTP success page is rejected. Verification is contract checking, not cryptographic server authentication; this demo has no authentication.

## Connect Android to the backend

1. Keep the API running and reachable. Android can launch before the server is available, but connection verification and task requests need a working server.
2. On first launch choose **Connect to Server**; later use the header's **Connection Settings** icon.
3. Enter a supported server URL. The secondary **Use emulator address** action fills in `http://10.0.2.2:5087/` only after you choose it. It never automatically selects or connects to a server. Otherwise enter your own address.
4. If using permitted local HTTP, review and acknowledge its unencrypted-traffic warning. Prefer HTTPS.
5. Choose **Test Connection** to check the draft without saving or selecting it. The probe validates both GET `/api/health` and GET `/api/tasks`.
6. Choose **Save & Connect** to persist and select the endpoint, then **Back to Task List**. Successful connection changes load tasks automatically. Refresh and Retry remain available.

The normalized URL is stored with Preferences DataStore and restored on future launches. Saved settings take precedence over any optional build fallback. **Save & Connect** remembers a valid address even if its server is temporarily unavailable. Failed validation/disk writes preserve the previous saved choice; connection failures never delete it.

### Emulator versus physical phone

- **Android emulator:** explicitly select `http://10.0.2.2:5087/` in Settings. `10.0.2.2` is the emulator alias for the computer's loopback listener. The local backend must be running on port 5087.
- **Physical device:** enter an HTTPS endpoint with a certificate trusted by Android and valid for its hostname. If hosted on your computer, it needs a listener reachable on the trusted LAN, suitable HTTPS configuration, device/host connectivity, and narrowly scoped firewall permission. The local HTTP-only command above does not provide that HTTPS endpoint. Changing `http` to `https` alone does not configure TLS.
- **Device `localhost`/`127.0.0.1`:** refers to the Android device itself, not the computer. Never use a wildcard binding such as `0.0.0.0` as the client address.

Stock APKs cannot add arbitrary private-LAN HTTP exceptions at runtime. Android network-security domain exceptions are packaged resources, not editable URL preferences. Entering an arbitrary LAN IP in Settings therefore does not authorize cleartext to that IP. Use supported HTTPS instead. Physical-device connectivity, including the optional custom debug method below, has not been verified.

A server listening only on `127.0.0.1` accepts connections from its own computer. The Android Emulator has the special `10.0.2.2` host-loopback mapping; a physical phone does not. A phone therefore needs a separately configured, reachable HTTPS listener with a trusted certificate. The source and prebuilt commands above deliberately do not expose a LAN or public server. Starting the backend, changing the URL scheme in the app, or changing the phone's settings alone does not supply that HTTPS setup.

## Managing tasks

- Select the **+** floating action button (**Create Task** for accessibility) at the bottom-right to open the shared task form. It hides while the software keyboard is visible and returns when it closes; its reserved bottom area keeps card controls unobstructed. Enter a nonblank title (up to 200 UTF-16 code units) and an optional description (up to 2,000). Content is preserved without trimming; an empty description is sent as null. Choose **Save** or **Cancel**.
- Choose the pencil icon (**Edit task**) on a card to change its title or description. The task ID, creation time, and completion status are preserved.
- Use a card’s completion checkbox to complete an active task or reopen a completed one. The checkbox changes only after the server confirms the update.
- Choose the trash icon (**Delete task**) on a card, or **Delete** in its edit form, then confirm the dialog identifying that task. **Cancel** leaves it intact. There is no undo.

Writes require a configured server. One mutation runs at a time; affected controls show progress and reject duplicates while filters and scrolling remain available. Errors retain form input or confirmed tasks for an explicit retry. Successful writes close the form/dialog, apply the confirmed response, and refresh the list without resetting the selected filter. A newly completed task disappears from the Active filter; a newly created task may be hidden by Completed. If the follow-up refresh fails, the confirmed change stays visible with a refresh warning and Retry action.

Changing the server cancels pending work, closes old drafts/confirmation, and rejects late responses from the previous repository. Cancellation cannot guarantee that a write already received by a server was rolled back; check that server’s list before repeating an uncertain operation. No writes are queued offline.

## Searching tasks

Use **Search tasks** to match a substring in the title or optional description of currently loaded tasks. Results update immediately without network calls. Search combines with **All**, **Active**, or **Completed**; counts always describe the entire loaded collection. The search field includes a search icon; its **×** icon (**Clear search**) removes only the query. The Search keyboard action dismisses the keyboard without submitting a request.

Matching ignores case and surrounding query whitespace. Unicode simple case matching treats Turkish **I**, **i**, **İ**, and **ı** alike, independently of the device language; this is intentionally broader than Turkish dictionary casing. Accents remain significant. Search does not perform transliteration, canonical Unicode normalization, multi-character case folding (such as **ß** to **ss**), fuzzy matching, or word tokenization.

The query and filter survive refresh, task mutations, form navigation, and Activity recreation through the retained ViewModel. They are not saved across process death. Changed tasks immediately enter or leave the results after server confirmation. **No matching tasks** means that tasks exist but none match both controls; try another query, change the filter, or clear search. **No tasks yet** means the server collection is empty. Search only covers the latest loaded collection; refresh explicitly to retrieve changes from other clients.

## Architecture and data flow

The app follows MVVM + Repository + unidirectional data flow with constructor injection, coroutines, and immutable StateFlow snapshots. Connection Settings uses saveable Activity-level navigation. The Home ViewModel owns the shared task form, delete confirmation, and mutation state, with native Back handling. No navigation framework is introduced.

Paths below are relative to `app/src/main/java/com/gurhangure/taskpilot/`:

```text
MainActivity.kt                      Screen navigation and lifecycle-aware state collection
feature/home/                        Task list, shared task editor, confirmed mutations, counts, filters
feature/connection/                  Editable URL, consent, save/test actions, verification feedback
TaskPilotApplication.kt              One process-scoped HTTP factory and one DataStore instance
data/settings/                      EndpointSettings contract and Preferences DataStore adapter
data/repository/                    Active/persistent connection selection and task operations
data/remote/                        URL policy, Retrofit routes/DTOs, strict JSON, shared OkHttp
data/model/                         Task snapshots, validation, and typed errors/results
ui/theme/ and ui/components/          Shared dark design and error guidance
```

Startup waits for asynchronous Preferences DataStore restoration before requesting tasks. With no saved/default endpoint it shows a friendly connection-required state and makes no invalid request. A saved or explicitly supplied endpoint triggers automatic list loading. Restoration errors are displayed rather than silently switching to a different build server. Settings stores only the normalized URL, using atomic DataStore edits. It is loaded across process restarts; no tasks are stored offline.

A save validates the URL, persists it, and then publishes the new endpoint. The Home ViewModel cancels older list requests, clears the previous server's data, reacquires the task repository, and loads the new list. Endpoint and request-version checks reject stale results. Successful Save & Connect verification also recovers an earlier list error, waiting for any active load before retrying; healthy data is not reloaded for an unchanged URL. Valid same-URL configuration reuses its repository. Retrofit services share one OkHttp client. Draft tests do not change the active endpoint; cancellation and version checks prevent superseded probes from replacing newer feedback. Leaving Settings cancels its pending verification.

The task list retains global counts, local search and filters, stable LazyColumn task-ID keys, manual refresh, and typed recovery messages. Visible results are derived once per immutable UI-state snapshot, without adding a search layer or modifying repository/API contracts. The header uses labelled settings and refresh icons; global metric cards share equal widths and heights, and filters wrap on narrow screens. Refresh preserves the last loaded collection and displays a warning on failure. This is transient screen state, not an offline cache. Activity recreation retains ViewModels and navigation selection.

The API supports GET/POST `/api/tasks` and GET/PUT/DELETE `/api/tasks/{id}`; the UI uses the existing create, replacement update, and delete repository methods, followed by list refresh. `RemoteTaskRepository` validates/marshals DTOs into UUID/Instant snapshots, preserves null/false write semantics, maps HTTP/transport/data errors, and rethrows cancellation. Connection verification requires HTTP 200 plus `status == "ok"` from `/api/health`, followed by a valid task array from `/api/tasks`. Health checks alone do not prove database availability; the task request verifies that boundary as well. Deployment path prefixes and trailing-slash normalization are preserved.

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

Versions are centralized in `gradle/libs.versions.toml`. Gradle may resolve higher compatible transitive AndroidX/coroutine versions. Follow [TASKPILOT_DESIGN_SYSTEM.md](TASKPILOT_DESIGN_SYSTEM.md); dark-only, no dynamic theme switching.

```sh
./gradlew :app:assembleDebug
```

Use `gradlew.bat` on Windows. The installable debug-signed APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. No release keystore or store publishing is configured. Android Studio manages the ignored local SDK path.

## Testing and GitHub Actions

[Android CI](https://github.com/gurhangure/taskpilot-android/actions/workflows/android.yml) runs JVM unit/integration tests, Android Lint, debug APK compilation, and separate API 35 emulator component and E2E suites. The existing test/build steps retain caching, timeouts, and read-only repository permissions. A dependent **Verified debug/demo APK** job runs only after all of them succeed, for pushes or manual runs on main in the designated repository. Pull requests and other branches never publish the demo. JVM tests include MockWebServer and temporary-file DataStore integration tests; they require neither a developer backend nor a real database. Automated tests are authored for CI and were not run locally.

The optional [demo APK job](#download-and-install-the-demo-apk) rebuilds the standard debug variant after validation, checks its signature with `apksigner`, and retains the artifact for 14 days. Test reports are retained for 7 days. Consult Actions for the result of the commit being reviewed; no current CI success is implied here.

| Level | Coverage |
| --- | --- |
| JVM unit | Fresh/restored settings, precedence, URL normalization/policy, save/failure, connection feedback, cancellation/stale results, live endpoint switching in the Home ViewModel, counts/filters/retry/refresh, CRUD validation/success/failure, duplicate prevention, confirmation, stale mutation isolation, title/description search, Turkish case matching, clear-search, and query/filter preservation |
| Integration | Real Retrofit/OkHttp/JSON against MockWebServer, health and task contracts, unrelated/malformed servers, HTTP errors, timeouts/cancellation, mutation-to-refresh ViewModel/HTTP flow with preserved search/filter and no search HTTP calls, including a failed reload, plus Preferences DataStore save/reopen on an isolated file |
| Compose components | Task List loading/empty/populated/error states, first-launch Connect action, Settings URL/actions/consent/status, accessible CRUD controls, form validation/progress, delete confirmation/errors, search/clear/IME interactions, conditional emulator guidance, and constrained-screen/large-font search and form controls |
| Emulator E2E | Real first-launch navigation into Settings, invalid URL handling without a network call, return to Task List, and Activity recreation; full app composition with real ViewModels and a deterministic repository for create/edit/complete/reopen/delete journeys with a retained description-search query |

Live API integration, physical devices, and API 26/36 runtime coverage remain unverified. The CRUD E2E journey injects a controlled repository into the same app composition; real HTTP contracts are covered separately by MockWebServer integration tests. Existing repository and task-list tests remain enabled.

## HTTP policy and optional development configuration

Both debug and release builds retain normal HTTPS certificate/hostname validation and block cleartext by default. They include one deliberate demo exception: exact host **10.0.2.2**, without subdomains. This lets an emulator user configure the local backend in-app without rebuilding. The user must acknowledge the unencrypted-HTTP warning before Settings sends HTTP requests. It is never used automatically on an emulator or physical device.

HTTP has no encryption or server authentication. Anyone with access to the network/backend can potentially observe or modify demo tasks. Use disposable data on a trusted network. No credentials, API keys, passwords, tokens, URL queries, fragments, or embedded user information are accepted or stored. Do not expose the unauthenticated backend to the public internet or bypass certificate validation.

| Mode | HTTPS | Emulator HTTP `10.0.2.2` | Other private-LAN HTTP |
| --- | --- | --- | --- |
| Default debug APK | Normal trust validation | Explicit in-app choice and acknowledgement | Blocked |
| Release build | Normal trust validation | Explicit, documented demo exception and acknowledgement | Blocked |
| Custom debug APK | Normal trust validation | Same exception | One exact private IPv4 host selected at build time; no subdomains |

The distributed demo does not include a custom private-LAN HTTP exception. Optional custom debug builds below are for developers and do not change the stock APK. Existing non-secret build properties remain supported for development and automated fixtures, but are optional. **Saved user settings always win over a build fallback**. A build fallback is not written to DataStore until the user saves it.

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

## Optional: APK installation and distribution

### Download and install the demo APK

1. Sign in to GitHub and open [TaskPilot Android → Actions → Android CI](https://github.com/gurhangure/taskpilot-android/actions/workflows/android.yml).
2. Select a **successful run on main** containing the **Verified debug/demo APK** job. Confirm that JVM/lint, component, E2E, and demo jobs succeeded for the commit you want.
3. Scroll to **Artifacts** at the bottom of the run summary and download **taskpilot-android-debug-demo-apk**. GitHub sign-in is required to download Actions artifacts, even from this public repository.
4. Unzip the downloaded artifact. Inside is **taskpilot-android-debug-demo.apk**. Android installs the APK, not the ZIP.
5. Transfer the APK to an Android 8.0/API 26 or newer device, or download it there directly and unzip it. Open the APK with the device's Files app or download manager and select **Install**.
6. If Android prompts you to **Allow from this source** / **Install unknown apps**, authorize only the specific installer you are using. Organization/device policy may block installation; follow that policy. The permission can be revoked after installation.
7. Open **TaskPilot**, start your backend separately, and follow [Connect Android to the backend](#connect-android-to-the-backend).

This is an **installable, debug-signed demo**, not a production release or an unsigned APK. It is debuggable and uses Android's development signing behavior; no private production keystore or release-signing infrastructure is configured. Hosted runners may generate different debug certificates between builds. If an update reports a conflicting package/signature, record your server URL before uninstalling the previous demo and installing the new one; uninstalling removes local app settings, not tasks stored on the server. The application ID remains `com.gurhangure.taskpilot`.

APK artifacts are retained for **14 days**. Publication requires successful JVM/lint, component, and E2E jobs; failed/cancelled validation jobs prevent it. Pull requests and runs on other branches do not publish the demo artifact. Always select an entirely successful run. If it is missing or expired, choose another successful eligible run or run from source; do not substitute an older APK that lacks the functionality you want. Check the selected commit’s Actions result before downloading. There is no permanent Android release download or automatic GitHub Release publishing.

**Physical-phone limitation:** the stock demo permits HTTPS with normal certificate/hostname validation and only the existing explicit emulator HTTP exception for `10.0.2.2`. Entering an arbitrary private-LAN HTTP URL does not authorize cleartext traffic. The [loopback-only HTTP backend command](#backend-setup) is intended for emulator use and does not configure HTTPS for a phone.

### Use a prebuilt backend instead of installing the .NET SDK

The existing [TaskPilot API distribution guide](https://github.com/gurhangure/taskpilot-api#download-and-run-a-self-contained-distribution) documents self-contained console packages. Open [TaskPilot API → Actions → CI](https://github.com/gurhangure/taskpilot-api/actions/workflows/ci.yml), choose a successful main run with the matching **Package and verify** job, and download its artifact after signing in:

| Computer | Artifact | Archive inside the artifact ZIP |
| --- | --- | --- |
| Windows x64 | `taskpilot-api-win-x64` | `taskpilot-api-win-x64.zip` |
| macOS Apple Silicon | `taskpilot-api-osx-arm64` | `taskpilot-api-osx-arm64.tar.gz` |
| macOS Intel | `taskpilot-api-osx-x64` | `taskpilot-api-osx-x64.tar.gz` |
| Linux x64 (supported glibc system) | `taskpilot-api-linux-x64` | `taskpilot-api-linux-x64.tar.gz` |

Extract the downloaded artifact ZIP, then extract the archive inside it. Keep all extracted files together, including `appsettings.json`. These are console applications, not installers. Platform artifacts expire after **30 days** and are distributed through Actions, not published GitHub Releases. The packages include .NET and SQLite support; no .NET SDK/runtime or separate database server is required. Linux still needs the native OS libraries listed in the backend guide.

From the fully extracted package directory on Windows (PowerShell):

```powershell
.\TaskPilot.Api.exe --urls http://127.0.0.1:5087
```

From the fully extracted package directory on macOS or Linux:

```sh
chmod u+x TaskPilot.Api
./TaskPilot.Api --urls http://127.0.0.1:5087
```

Use the package matching your computer's architecture. These executables are unsigned; macOS packages are not notarized. Follow the backend guide and your OS/organization policy if execution is blocked; do not disable system protections. Keep the terminal running and stop with Ctrl+C. The commands explicitly choose port **5087** to match the Android emulator suggestion; other backend examples may use 5080.

Packaged startup creates its per-user data directory and initializes/migrates SQLite automatically while retaining existing data. Source startup also applies migrations automatically. No EF CLI or manual database creation is needed to evaluate either mode.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| APK artifact is missing or download asks for sign-in | Sign in, choose an eligible successful main run with the demo job, and check its 14-day retention. Failed/cancelled validation jobs, PRs, and other branches do not publish it |
| Android will not install the download | Unzip first and open the APK on Android 8.0/API 26+. Check free space, the specific installer permission, and device policy. For a signature conflict, see the [debug-signing note](#download-and-install-the-demo-apk) |
| Android SDK missing / Gradle Sync fails | Install Platform 36 and Build Tools 36.0.0 in SDK Manager, accept licenses, use JDK 25 and compatible Android Studio, and check repository/dependency network access; do not copy another machine’s SDK paths |
| Address or port is wrong | Keep the API running on the exact port in Settings. Use 5087 with the commands above; emulator host mapping and physical-phone HTTPS are different |
| Save is rejected | Check the title/description field messages and limits; input is retained for correction |
| A mutation fails or a task was removed elsewhere | Review the displayed error, cancel if needed, refresh, and retry against the current server; there is no automatic write retry |
| A saved task is hidden | Check the search query and Active/Completed filter; clear search or choose All. Counts always use all loaded tasks |
| Search misses an expected task | Search only examines loaded titles/descriptions. Check accents and the status filter, clear the query, or explicitly refresh for changes from another client |
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

No authentication, offline cache, background sync, QR pairing, undo, fuzzy/server-side search, or light theme. Concurrent clients use the backend’s existing replacement-update contract without version/conflict detection. The API has no authentication or user isolation. Connection verification checks documented contracts and cannot authenticate an adversarial server. URL configuration is persistent, but task data and draft feedback are not stored across process death. No public release, production server, or store signing is configured.

`AGENTS.md`, numbered `prompts/`, SDK paths, IDE state, credentials, and signing files remain ignored/untracked. The public design specification remains tracked.
