# brownSigns

See `README.md` for what this is and how to rebuild the dataset and the app.

Notes for anyone working here:

- **The UI lives in `mobile/shared/src/commonMain`**, not in `mobile/app`. The
  Android module is a launcher, a repository, a location source and a ViewModel;
  every composable is common code. Put new UI there unless it genuinely needs an
  Android API.
- **Location goes through `LocationManager`, not Play Services.** That is
  deliberate — it works on any Android device and in an emulator being fed
  `adb emu geo fix`, which the fused provider did not. Don't "upgrade" it.
- **No-location and no-compass are designed states, not failures.** Without a fix
  the list is alphabetical and says so; without a compass the direction is named
  instead of drawn. Don't replace either with a guessed origin or a north-up
  arrow — the list would look correct and be wrong.
- **Editing the dataset means editing `scripts/build-dataset.mjs`**, never
  `data/sites.json` by hand — it is generated, and hand edits vanish on the next
  rebuild. After changing it, re-run with `--offline` and rebuild the APK so the
  bundled asset matches.
- **Ordering by a route needs `MOTIS_URL` in `.env.local`** (see `.env.example`).
  The road and the address search both come from `@automatelinux/geo`
  (`/opt/automateLinux/packages/geo`) — the same code publicTransportation and
  midreshaze use. Never add a geocoder or a MOTIS client to this app. The
  word "יעד" means only where the user is driving; the list's entries are "אתרים"
  (when both were "יעדים", the list's search box was taken for the destination
  field). The phone measures every site against that road
  itself (`geo/RouteLine.kt`) and keeps the route in `route.json`, so the order
  holds with no signal. A route is never stood in for by a straight line — with
  no road there is no route ordering, and the header says why.
- **Android builds run on the desktop, never on the leader** (see the root
  `.claude/CLAUDE.md`).
