# CLAUDE.md

## Build Commands

```bash
# Debug build (via Android Studio or CLI)
./gradlew assembleDebug

# Release build — use the bundled script (handles APK + AAB + mapping copy)
# By default it also uploads to Play internal and promotes to the rooti track; add --no-publish for a local build
bash .claude/commands/build_release.sh
# Outputs: apk/<versionName>/Tag&Go_V<version>.{apk,aab} and mapping_V<version>.txt

# Run instrumented tests
./gradlew connectedAndroidTest

# Unit tests
./gradlew test
```

For release builds via Claude Code, use `/build_release` — it runs the script inside tmux and monitors progress.

## Architecture Overview

Single-module Android app using **Jetpack Compose** + **MVVM** + manual DI (no Hilt).

### Dependency Injection

`ServiceLocator` (`di/AppModule.kt`) is a hand-rolled singleton that wires everything. Call `ServiceLocator.init(context)` once in `Application.onCreate()`. `ServiceLocator.reinitWithBaseUrl(url)` rebuilds both Retrofit instances and the repository without recreating the database; `login()` calls it when Phase 1 starts, not the server picker (see Server Regions).

### Network Layer

Two separate OkHttp + Retrofit stacks:

| Client | Auth | Used for |
|--------|------|---------|
| `authApi` | Basic Auth header | `POST /oauth/token` only |
| `rootiCareApi` | Bearer token via `AuthInterceptor` | all other APIs |

JSON serialisation uses **Moshi with reflection** (`KotlinJsonAdapterFactory`) — no Moshi codegen.

### Data Flow

```
Screen (Compose) → ViewModel → RootiCareRepository → {Retrofit API | Room DB}
```

ViewModels access `ServiceLocator.repository` directly. There is no StateFlow; state is held in Compose `mutableStateOf` inside ViewModels.

### Local Persistence

`AppDatabase` (Room v2) stores a single table: `event_tags` (`EventTagDbEntity`). The database uses `fallbackToDestructiveMigration()` — schema changes destroy existing data. The `Converters` class serialises `List<Int>` (symptom type IDs) as a comma-separated string.

`TokenManager` persists auth state in `SharedPreferences` (`rooticare_prefs`). It preserves `deviceId`, `push_token`, `server_url`, `last_logged_out_id`, and `server_device_id` across `clearAll()` calls, plus `offline_logout_pending` unless `clearPendingLogout=true` — everything else is wiped on logout. No current code sets `offline_logout_pending` to true; only prefs written by older versions carry it, so the 409 repair in `startPhase2` runs only for those installs.

### Navigation

`AppNavigation.kt` uses Navigation Compose with four routes: `login` → `main` → `history` / `profile`. Navigation always clears the back stack when crossing the login boundary.

### Key Business Logic

**Login flow** (see `docs/api.md` for the full state diagram):
1. `POST /oauth/token` — exchange Basic auth for bearer token
2. `GET recordingMeasurements` — fetch active recording devices for this patient
3. User selects device → `GET authPatient` — subscribe patient to that measure session
4. `GET getCurrentMeasurementInfo` — verify recording is active (`state == 0`, `mode == 0`)
5. If success, paginate-download all event tag history into Room

`measureRecordId` is immutable after login — it is only written once (at step 4) and only cleared on explicit logout. Background status checks must never overwrite it. `isLoggedIn` is written in the same step 4 commit, and cold start enters `main` only through `TokenManager.hasActiveSession`, so a login that fails after step 3 returns to the login screen.

A device list and selection belong to the region and the institution/patient they were fetched for: changing either discards them, so the next `login()` runs Phase 1. `NOT_MEASURING` and `UNSUPPORTED_MODE` discard the selection for the same reason.

**Logout** calls `unsubscribePatient` API then `clearLocalData()`. If the network is unavailable, logout fails and local state is preserved (no offline auto-clear).

**Event Tag sync**: local tags get a `TAG-<timestamp>` ID. A successful upload's response has no tag IDs, so the local record keeps its `TAG-` ID and is marked `isEdit = false, isRead = true` (synced). On a 2xx with `failedSize > 0`, tags listed in `failedAdding` (matched by `tagTime`) stay unsynced and the call returns failure; if that list cannot account for every rejection, the whole batch stays unsynced.

### R8 / ProGuard (release builds)

Minification is enabled. Known critical rules in `app/proguard-rules.pro`:

- `-keep interface com.rootilabs.wmeCardiac.data.api.**` + `-keep class kotlin.coroutines.Continuation` + `-keep class retrofit2.Response` — R8 renames `Continuation` and `Response` but does NOT update their references inside Signature attributes of explicitly kept classes; JVM can't resolve the original names → `getGenericParameterTypes()` returns raw `Class` instead of `ParameterizedType` → `ClassCastException` in `HttpServiceMethod.parseAnnotations:46`
- `-keep class com.google.mlkit.vision.barcode.BarcodeScanning` — prevents R8 from removing the class and its `<clinit>`, which would cause NPE when the barcode scanner is opened
- `-keep class com.google.mlkit.**` — required companion rule

When a release crash shows obfuscated class names, retrace with:
```bash
java -cp "/Applications/Android Studio.app/Contents/plugins/android/lib/r8.jar" \
     com.android.tools.r8.retrace.Retrace \
     apk/<version>/mapping_V<version>.txt <stacktrace.txt>
```
Historical mappings are archived under `apk/<versionName>/mapping_V<versionName>.txt`.

## Key Files

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | Version code/name, signing config, Firebase App Distribution release notes |
| `keystore.properties` | Signing credentials (not committed) |
| `Constants.kt` | Default `BASE_URL`, region URL constants and `BASIC_AUTH` credential |
| `di/AppModule.kt` | `ServiceLocator` — the wiring of all singletons |
| `data/auth/TokenManager.kt` | SharedPreferences wrapper for all auth/session state |
| `data/repository/RootiCareRepository.kt` | All API calls except `TokenAuthenticator`'s token refresh, and local DB operations |
| `app/proguard-rules.pro` | R8 keep rules — touch with caution |
| `docs/api.md` | Full API reference with error codes and login flow diagram |

## Server Regions

```kotlin
AP_API_URL  = "https://mct-api.rooticare.com/"   // default
AP2_API_URL = "https://mct2-api.rooticare.com/"
AP3_API_URL = "https://mct3-api.rooticare.com/"
EU_API_URL  = "https://mcteu-api.rooticare.com/"
DEV_API_URL = "http://192.168.103.17:8080/"       // local SIT
```

The login picker reads the `ServerRegion` enum (`ui/login/LoginViewModel.kt`), whose URLs are literals: adding or changing a region means editing the enum, and the region constants in `Constants.kt` only feed `BASE_URL`. `DEV_API_URL` has no call site and no enum entry, so SIT is not selectable in the app.

`onServerSelected` writes `TokenManager.serverUrl`; when the region actually changes it also discards the device list and selected device, which belong to the old region, so the next `login()` runs Phase 1 and `ServiceLocator.reinitWithBaseUrl()` rebuilds Retrofit for the new URL. A same-region retry with a device already selected goes straight to Phase 2 without a rebuild. The old region's access token stays in `TokenManager` until Phase 1's `getToken` replaces it. Cold start builds Retrofit in `ServiceLocator.init()` from the persisted URL.

## Localisation

UI strings live in `res/values/strings.xml` (English base) with locale overrides in `values-zh-rTW`, `values-de`, `values-fr`, `values-es`, `values-it`, `values-ja`, `values-nl`. Error messages returned by the API are in English. `LoginScreen` translates the login error codes it lists via string resources and shows anything else verbatim (raw exception messages, and the hard-coded Chinese invalid-response message from `authPatient`). Logout errors are hard-coded Chinese strings in `MainViewModel`, `ProfileViewModel` and `RootiCareRepository`, or the server's error body when it sends one.
