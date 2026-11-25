<<<<<<< HEAD
# CropCompass_MobileApp
MAD Project
=======
# Crop Compass

Native Kotlin / Jetpack Compose application for Karnataka mandi discovery, estimated farmer revenue, local alerts and farmer reports. Package: `com.cropcompass.app`. Phase 1 authentication and the green theme are retained.

**No real CSV has been supplied. The installed bundle is TEST DATA: all prices, arrivals, market identities and coordinate positions in the fixture are synthetic.** A persistent banner identifies this throughout the app. Real data is never replaced silently by test values.

## Run in Android Studio

Open `D:\desktop\MAD+BDA`, sync Gradle, and run `app` on Android 8.0/API 26 or newer. The project uses SDK 35, Gradle 8.9, AGP 8.7.3, Kotlin/Compose compiler 2.0.21 and Java 17 or 21. Android Studio's bundled Java 21 runtime is installed here.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Current results and limitations are in [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md); device screenshots are in [docs/screenshots](docs/screenshots).

Choose **Explore guest demo** without Firebase configuration. First-run onboarding asks for District → Mandi. Districts without supplied mandi records explain that no records were supplied. Save the selection; Home, default price filters and comparison origin react to the same DataStore preference. Profile edits the selection and transport/radius assumptions. Location suggestions are optional, with a rationale and manual fallback.

## Load actual Agmarknet data

Python 3.10+ and pandas are required. The tooling is separate from Android and does not depend on Spark or a running backend.

```powershell
python -m pip install -r tools/requirements.txt
# Put the real Agmarknet CSV file(s) in data/raw/, then:
python tools/build_karnataka_data.py --days 365
python -m unittest discover -s tools -p test_pipeline.py -v
.\gradlew.bat :app:assembleDebug
```

The builder reads every `.csv` in `data/raw/` in 50,000-row chunks and stages deduplicated rows in temporary SQLite. It reports detected column aliases. Required fields are observation date, Karnataka state, district, market, commodity, modal price and an explicit price unit; market codes, varieties, arrivals and coordinates are retained when supplied. Unknown units and invalid/non-positive prices/dates are rejected, not guessed. Prices convert explicitly to INR per quintal. `arrivals_tonnes`/`arrivals_in_tonnes` are tonnes; unspecified arrivals stay missing.

Outputs are `app/src/main/assets/data/{districts.json,mandis.json,commodities.json,prices.csv,metadata.json}` and `tools/data_quality_report.md`. The price history window reduces from 365 → 180 → 90 days when needed to keep data assets below 30 MiB; failed oversized candidates are temporary, not shipped. Metadata records the selected window, actual date range and count. Room checks the source-aware content fingerprint before replacing market tables. A changed bundle preserves watchlists and reports. There is no need to clear app storage to import a newer bundle.

Fill optional override files before rebuilding:

- `tools/alias_map.csv`: `market_code,alias,canonical_name`; market code remains the preferred identity.
- `tools/manual_coords.csv`: `market_code,lat,lng`; these override source coordinates. Out-of-box values are rejected and reported.
- `tools/names_kn.csv`: `english,kannada`; only confident translations are prefilled. Missing names fall back to English and are listed in the quality report.
- `tools/district_aliases.csv`: historical district spellings mapped to the reference directory, so a Mysore row appears under Mysuru instead of creating a second district. Bengaluru South retains Ramanagara as an input alias; the name change is documented on the [official district history page](https://bengalurusouth.nic.in/en/history/).

The 31-name reference district directory is supplemented by distinct source district aliases when necessary; no mandi is invented to fill an empty district. Missing coordinates remain absent unless you explicitly run geocoding. Markers with district-level positions are labelled approximate.

Optional geocoding uses Nominatim at no more than one request per second, with a persistent cache. Supply your own identifying User-Agent/contact:

```powershell
python tools/build_karnataka_data.py --days 365 --geocode --user-agent 'CropCompassCollege/1.0 (contact: YOUR_EMAIL)'
```

It queries market + district + Karnataka, then a district centroid if no market match exists. Failed lookups retain missing coordinates. No external geocoding was performed during development. See the quality report before treating coordinates as exact. The standalone `tools/geocode_mandis.py --user-agent ...` helper uses the same builder and refreshes the fingerprint/report.

## Test fixture workflow

If `data/raw/` has no CSVs, the builder generates `tools/fixtures/synthetic_test_prices.csv`: 60 calendar days with deliberate gaps, six synthetic markets in three districts, and five crops. It never fills missing daily observations. The snapshot date defaults to the day you run the command; it can be pinned for reproducibility:

```powershell
python tools/build_karnataka_data.py --fixture-end-date 2026-11-06
```

A fixture file keeps its fixture label if deliberately used as input. Synthetic market IDs are refused in a bundle labelled real. Keep real and fixture files separate. Staleness uses the actual device date, so regenerate the fixture for later demonstrations; old test observations appropriately stop triggering alerts after 7 days and comparisons exclude them after 30 days.

## Features

Home searches English/Kannada crop names, displays matching latest observations at the saved mandi, keeps recent searches, shows changes/movers and target summaries, and links to comparison, reports, district browsing and maps. Prices filter district → mandi and commodity, sort by price/change/arrivals/distance/date and switch between list and map. Detail screens identify market/date/variety/units/source and offer actual observed history, arrivals, cross-mandi prices and estimates.

Comparison uses compatible commodity/variety/unit/source observations, rejects prices older than 30 days and warns beyond 7 days or for mixed dates. Estimated road distance is haversine × 1.3. Transport = road km × editable INR/quintal/km rate × quantity; net = quantity × modal price − transport. It ranks results, distinguishes highest price from highest net, and supports matched-date mandi comparisons. Default rate ₹0.05 is an assumption, not a freight quote.

Maps use osmdroid/OpenStreetMap without a key. Offline markers, clustering, text alternatives and marker sheets work independently of background tiles. Tiles are opt-in, cached and attributed; no bulk tile downloading occurs. Real-coordinate navigation opens an external maps app; navigation is disabled for synthetic coordinates. Price-explorer maps respect region filters, and comparison maps display the ranked candidate quotes.

Watchlists are stored in Room per local guest or Firebase UID. Targets have direction, enabled state and last-check/trigger timestamps. WorkManager checks at periodic intervals (at least 15 minutes, subject to Android scheduling); manual checks and a debug-only simulation exercise notifications. Android 13+ notification permission is requested from Watchlist. A target crossing only triggers on observations at most 7 days old and does not repeat continuously while the condition remains met.

Farmer reports support create/view/edit/delete and optional camera/gallery photos. Images are copied into private app storage, resized/orientation-corrected and encoded as JPEG. Gallery uses the system document picker; camera capture is delegated to a camera app through FileProvider. Broad media/camera permissions are unnecessary. Guest reports remain on device; **Sync my device reports** assigns guest reports to the signed-in user and queues sync. Reports are always unverified and are never used in official charts, statistics or recommendations.

Forecasts use a separate `PriceForecaster` interface and `LinearTrendForecaster` implementation. It fits a 30-calendar-day linear trend to three-observation averages, emits seven dated estimates with a dotted chart, and reports rolling-origin MAE over available dates in the last 14 days. Missing dates are not generated as historical records; stale history does not produce a current forecast. This is labelled an estimate, not advice.

Optional Spark files are documented in [docs/analytics_schema.md](docs/analytics_schema.md). Missing files hide their cards; there are no fallback analytics numbers. About the data shows the source, date range, records, bundle version, fixture flag and cost assumptions.

English/Kannada switching uses AppCompat application locales and DataStore. Core navigation, action labels and available crop/district names change; some detailed explanatory/error text remains English. Missing Kannada catalog names intentionally fall back to English.

## Firebase setup

1. Register Android package `com.cropcompass.app` in your Firebase project.
2. Place its `google-services.json` in `app/` (ignored by Git).
3. Enable Email/Password Authentication, Firestore and Storage.
4. Deploy ownership rules and rebuild:

```powershell
firebase deploy --only firestore:rules,storage --project YOUR_PROJECT_ID
.\gradlew.bat :app:assembleDebug
```

User profiles are `users/{uid}`. Synced reports are `price_reports/{reportId}` and private photos are `report_photos/{uid}/{reportId}.jpg`. Server rules require authenticated ownership, immutable identity/creation fields and unverified report status; photos must be image content below 5 MiB. No plaintext password is stored. Firebase sessions persist. Local records remain available if sync fails; WorkManager retries when connected. Pending local edits take precedence over downloaded reports.

No Firebase project configuration is bundled. Real Android sign-in, account-session restoration and cloud/photo synchronization were not claimed tested against a user project. Rules can be tested entirely locally without credentials:

```powershell
npm.cmd ci
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
npm.cmd run test:rules
```

The script starts only local Firestore/Storage emulators for `demo-cropcompass`, runs ownership/validation tests, then stops them. It does not deploy or write to your Firebase project.

## Optional official refresh

Only when using a real CSV bundle, add your verified Variety-wise Daily Market Prices resource and key to ignored `local.properties`:

```properties
DATA_GOV_API_KEY=YOUR_KEY
DATA_GOV_RESOURCE_ID=YOUR_VERIFIED_RESOURCE_ID
```

No resource identifier is hardcoded or represented as verified. Retrofit requests `https://api.data.gov.in/resource/{id}` with the Karnataka filter, JSON format and pagination. Matching existing catalog identities is required; invalid/unmatched rows are skipped. Prices must be the selected resource's documented INR/quintal fields; an explicitly incompatible returned unit is rejected. No key/resource means no refresh request or periodic refresh job. Configured refresh preserves cached records on HTTP/timeout failure and never switches data mode silently. A real government API call was not run without your credentials.

The Phase 1 `BACKEND_BASE_URL` setting is retained for future FastAPI integration and is currently unused. For a host backend from Android Emulator, use `http://10.0.2.2:8000/`; emulator `localhost` refers to the emulator. Spark remains optional, via the versioned analytics-file contract.

## Architecture and test scope

Hilt owns the application container and market ViewModel. Room repositories expose Flow, ViewModels expose StateFlow, Compose observes lifecycle-aware state, and DataStore holds region/language/cost preferences. Domain classes isolate recommendation, distance, search/staleness, CSV parsing and forecast/backtest logic. WorkManager owns periodic alerts, configured refresh and authenticated report synchronization. No WebView, machine-learning service or analytics pipeline is required to launch.

Instrumented acceptance scenarios use the labelled fixture's synthetic IDs; run those scenarios with a fixture bundle. Room reseeding tests also verify preservation of personal records. Unit tests use isolated synthetic inputs. Rule tests use only local emulators. Screenshots and precise passed/unverified checks are recorded in `IMPLEMENTATION_STATUS.md`.
>>>>>>> bfb4e34 (Initial commit)
