# שלט חום — brownSigns

A brown road sign points at a tourist destination: a national park, a nature
reserve, a museum, an archaeological or heritage site, an attraction, a
viewpoint. This app is every one of them in the country, ordered by how far it
is from where you are standing, with an arrow that keeps pointing at it as you
turn.

**5,997 destinations** — 116 national parks, 347 nature reserves, 338 museums,
2,436 archaeological sites, 1,093 heritage sites, 750 attractions, 917 viewpoints.

## The pieces

| | |
| :-- | :-- |
| `mobile/` | The app. KMP / Compose Multiplatform — all of it (models, geo, ranking, UI) in `shared/commonMain`, so the iOS target is a launcher away. Android package `com.automatelinux.brownSigns.dev`, launcher name **שלט חום**. |
| `app/`, `lib/` | Next.js backend on **3147** (dev) / 3146 (prod), and the same list as a web page. Dev host: https://brownsigns.dev.ya-niv.com |
| `scripts/build-dataset.mjs` | Builds `data/sites.json` from OpenStreetMap via Overpass. |
| `data/sites.json` | The dataset. Committed, and copied into the APK's assets. |

## Rebuilding the dataset

```bash
node scripts/build-dataset.mjs             # re-query Overpass (~30 s)
node scripts/build-dataset.mjs --offline   # rebuild from .cache/, for filter changes
```

It writes `data/sites.json` **and** `mobile/app/src/main/assets/sites.json`, and
refuses to write a dataset under 2,000 sites — that only ever means a query or a
filter broke, and shipping it would be an app with no content and no error.

Which OSM features count as a destination, and which are dropped (surveyors'
labels, boundary stones, roadside cannons), is decided in one place: the
`HISTORIC` / `TOURISM` maps and the two name filters at the top of the script.

## Building the app

```bash
cd mobile && ./gradlew :app:assembleDevDebug
/opt/automateLinux/utilities/chunked-adb-install.sh \
  app/build/outputs/apk/dev/debug/app-dev-debug.apk 10.7.0.3:5555 com.automatelinux.brownSigns.dev
```

`mobile/.env` (gitignored) holds `API_BASE_URL`, baked in at build time. It points
at the desktop over WireGuard — `http://10.7.0.2:3147/`.

## Two rules the app is built on

**The list is complete offline.** The dataset ships inside the APK, because the
places it lists are exactly where there is no signal. The backend is only how a
*newer* list arrives.

**Newer means newer.** The dataset version is a content hash, so "different" says
nothing about which way time runs. Both ends compare `generatedAt`, and a
downloaded copy that is not newer than the one in the APK is deleted. Skipping
that once cost the phone 62 sites.
