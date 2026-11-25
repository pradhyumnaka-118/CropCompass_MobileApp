# Crop Compass — phases A–F

Real Agmarknet CSV has **not been supplied**. Phase A generates a reproducible, synthetic test fixture. Its market identities, prices, arrivals and coordinate positions are test values, not government observations. No fixture record may be relabelled as real.

## Phase A — verified fixture pipeline

Implemented: chunked pandas ingestion, Karnataka filter, explicit unit conversion, date validation, SQLite staging/deduplication, bounded history assets, coordinate validation/manual overrides, optional rate-limited cached Nominatim, Kannada name overrides, quality report and reproducible fixture tooling.

Ran `python -m unittest discover -s tools -p test_pipeline.py -v`: 3 tests passed after fixing Windows SQLite cleanup. Ran `python tools/build_karnataka_data.py`: 1,650 synthetic observations, 6 synthetic mandis, 5 crops, 31 reference districts, 60 actual history days (2026-09-07 through 2026-11-05), well below 30 MB. Ran `assembleDebug`, `testDebugUnitTest` (2 passed) and `connectedDebugAndroidTest` (2 passed) after bundling assets. Real CSV and network geocoding remain unverified/not supplied; no Nominatim requests were made.

## Phase B — verified offline region selection

Hilt/Room compile successfully. Added anchored selectors, first-run onboarding, dependent district/mandi lists, DataStore region/settings persistence, a permission-rationale/location suggestion flow, and AppCompat locale switching with English/Kannada core resources. Ran assembleDebug, testDebugUnitTest (6 passed), connectedDebugAndroidTest (4 passed). Region test selected Mandya → TEST Market 3 and verified Home/Profile; Room test verified re-seeding preserves personal tables. Screenshots captured for login, registration, onboarding, profile. GPS permission/provider behavior and Kannada recreation still need integration checks. No actual GPS or external geocoding is claimed verified.

## Phase C — verified fixture browsing

Implemented Home suggestions/recent searches, observed prices and changes, 7-day movers, dependent price filters and sorts, district/mandi browsing, commodity detail and Vico min/modal/max and arrivals charts. Ran assembleDebug, testDebugUnitTest (6 passed), connectedDebugAndroidTest (6 passed), including search→detail, range switches, price filtering, district→mandi navigation. All prices identify their mandi/date/unit and fixture status. Missing dates are not interpolated into records. Screenshots saved; chart framing is being improved in the next capture run.

## Phase D — verified fixture comparison/map flow

Implemented deterministic ranking, editable quantity/origins/rate/radius, same-variety/unit/source checks, exclusion beyond 30 days, age/date warnings, Vico net bars and matched-date mandi overlays. osmdroid renders offline markers, zoom-dependent clusters, price colors, approximate coordinate styles and a detail sheet with actions; tiles are opt-in and attributed. A persistent test-data banner remains visible while scrolling. Ran assembleDebug, testDebugUnitTest (10 passed), connectedDebugAndroidTest (7 passed). Map device test used the accessible marker text alternative, which shares the marker callback; direct geographic marker tapping and external maps navigation are not yet claimed tested. Synthetic-coordinate external navigation is deliberately disabled.

## Phase E — verified local CRUD/notifications and emulator rules

Implemented Room watchlist add/edit/enable/delete/swipe, WorkManager target checks, permission handling, notification channel and debug alert simulation. Implemented report CRUD, private JPEG copies from gallery/camera, Firebase-owned background upload/download/delete and photo storage, plus configured-only Retrofit refresh with timeout/retry and cached retention. Ran assembleDebug, testDebugUnitTest (13 passed), connectedDebugAndroidTest (9 passed), and npm run test:rules (9 passed on local Firestore/Storage emulators). Actual notifications were asserted in NotificationManager; the gallery test supplied a stubbed system-picker result, then verified a real app-owned JPEG and Room report, edit and deletion. Report saves did not alter official price counts. Fixed transient feedback interception in edit tests. The actual external camera, real Firebase account/sync and live data.gov.in response remain unverified without credentials/device setup; ownership rules were tested locally, not deployed.

## Phase F — implemented and verified with test data

Implemented the swappable on-device forecast, seven future estimates, walk-forward MAE without future leakage, optional version/source-checked Spark insight loading, and data provenance page. Forecast charts display the complete observed/estimated interval. Missing analytics files show an honest empty state. Kannada locale recreation, actual disconnected-network cached browsing, direct geographic marker taps, notifications, and report photo cleanup passed device tests. These final checks supersede the earlier phase-by-phase test limitations for those flows.

## Final verification — 6 November 2026

- Pipeline: **5 tests passed**, including deterministic fixture generation, cleaning/deduplication, units/dates/coordinates, historical district aliases, missing identities and safe Unicode identifiers.
- Android: **16 unit tests and 13 device tests passed**, on the Pixel 10 Pro Android 17 emulator. `assembleDebug` and `lintDebug` passed; lint has no errors.
- Firebase: **9 ownership/security tests passed** against local Firestore/Storage emulators. Rules have not been deployed to a real project.
- Delivery: debug APK at `app/build/outputs/apk/debug/app-debug.apk`, **26,092,198 bytes**. Its bundled data was compared byte-for-byte with the current source assets.
- Assets: **154,164 bytes**, 1,650 synthetic price observations, 6 synthetic mandis, 5 crops and 31 reference districts. Actual observations span **8 September–6 November 2026**; `historyDays: 365` is the retention cap, not a claim of 365 observed days. Data version: `19e84eb3bbbb067a499c`.
- Evidence: **25 device screenshots**, indexed in [docs/screenshots/README.md](docs/screenshots/README.md). Test reports are under `app/build/reports/`.

Reproduce Android verification using Java 21:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain
python -m unittest discover -s tools -p test_pipeline.py -v
npm.cmd run test:rules
```

Connected tests install/remove the instrumented application; use a disposable emulator for repeat runs.

## Configuration and verification still required

The delivered app is functional with its prominently labelled **TEST DATA — not real prices** fixture. No real Agmarknet CSV was supplied. Add real CSV files to `data/raw/`, run the documented pipeline and rebuild before evaluating real market recommendations. Large real-data performance, live geocoding and real-data coverage have not been measured.

Real accounts and cloud report/photo synchronization require `google-services.json`, enabled Firebase providers/services, and deployed rules. Live Firebase sign-in, account restoration, report upload/download and remote deletion have not been verified. Local CRUD and emulator ownership rules have been verified. Optional live data.gov.in refresh requires a key and verified resource ID; no live API payload was tested. Optional Spark JSON files were not supplied, so populated insight cards are intentionally absent.

Actual GPS permission/provider behavior, the external camera and navigation to real coordinates have not been tested. The photo device test stubs the system-picker response, then verifies real image import, persistence, preview and deletion. Core navigation/actions and available name overrides support Kannada; some detailed help/error text remains English. Synthetic coordinates are labelled approximate and external navigation is disabled for fixture markets.
