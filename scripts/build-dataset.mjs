#!/usr/bin/env node
// Builds data/sites.json — every destination in Israel that a brown tourist sign
// points at: national parks, nature reserves, museums, archaeological sites,
// heritage sites, attractions and viewpoints.
//
// Source: OpenStreetMap via Overpass (ODbL). Re-run to refresh:
//   node scripts/build-dataset.mjs
// Add --offline to rebuild from the cached Overpass responses in .cache/.

import { writeFile, readFile, mkdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const CACHE = path.join(ROOT, '.cache')
const OFFLINE = process.argv.includes('--offline')

// Israel + the areas its brown signage covers.
const BBOX = '29.3,34.2,33.45,35.95'

const MIRRORS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
  'https://overpass.osm.ch/api/interpreter',
]

const QUERIES = {
  poi: `[out:json][timeout:250];
(
  node["tourism"~"^(museum|attraction|viewpoint|zoo|theme_park|aquarium|gallery)$"](${BBOX});
  way["tourism"~"^(museum|attraction|viewpoint|zoo|theme_park|aquarium|gallery)$"](${BBOX});
  node["historic"](${BBOX});
  way["historic"](${BBOX});
);
out center tags;`,
  protected: `[out:json][timeout:250];
(
  way["boundary"="protected_area"](${BBOX});
  relation["boundary"="protected_area"](${BBOX});
  way["leisure"="nature_reserve"](${BBOX});
  relation["leisure"="nature_reserve"](${BBOX});
  node["leisure"="nature_reserve"](${BBOX});
);
out center tags;`,
}

async function overpass(name, query) {
  const cached = path.join(CACHE, `${name}.json`)
  if (OFFLINE) {
    if (!existsSync(cached)) throw new Error(`--offline but no cache at ${cached}`)
    return JSON.parse(await readFile(cached, 'utf8'))
  }
  let lastErr
  for (const url of MIRRORS) {
    try {
      process.stderr.write(`  ${name}: ${new URL(url).host} … `)
      const res = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          // Overpass answers 406 to a request with no User-Agent.
          'User-Agent': 'brownSigns-dataset/1.0 (+https://github.com/yanivprusman/brownSigns)',
          Accept: 'application/json',
        },
        body: new URLSearchParams({ data: query }),
        signal: AbortSignal.timeout(280_000),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const json = await res.json()
      // A mirror that answers 200 with an empty element list has not served the
      // query — accepting it would silently produce an empty dataset.
      if (!Array.isArray(json.elements) || json.elements.length === 0) throw new Error('empty result')
      process.stderr.write(`${json.elements.length} elements\n`)
      await mkdir(CACHE, { recursive: true })
      await writeFile(cached, JSON.stringify(json))
      return json
    } catch (err) {
      process.stderr.write(`${err.message}\n`)
      lastErr = err
    }
  }
  throw new Error(`every Overpass mirror failed for "${name}": ${lastErr?.message}`)
}

// ── Categories ────────────────────────────────────────────────────────────────
// Order is the merge priority: when two OSM features describe one place, the
// earlier category wins.
const CATEGORIES = [
  'national_park',
  'nature_reserve',
  'museum',
  'archaeology',
  'heritage',
  'attraction',
  'viewpoint',
]
const RANK = Object.fromEntries(CATEGORIES.map((c, i) => [c, i]))

// historic=* values that are real signposted destinations, mapped to a category.
const HISTORIC = {
  archaeological_site: 'archaeology',
  ruins: 'archaeology',
  aqueduct: 'archaeology',
  citywalls: 'archaeology',
  city_gate: 'archaeology',
  castle: 'archaeology',
  fort: 'archaeology',
  tower: 'archaeology',
  tomb: 'archaeology',
  wine_press: 'archaeology',
  monument: 'heritage',
  memorial: 'heritage',
  battlefield: 'heritage',
  heritage: 'heritage',
  building: 'heritage',
  farm: 'heritage',
  church: 'heritage',
  manor: 'heritage',
  yes: 'heritage',
}
// Deliberately dropped: boundary_stone, wayside_shrine, wayside_cross, milestone,
// cannon, tank, aircraft, vehicle, locomotive, railway_car, wreck, mine,
// mine_crater, petroglyph — real OSM objects, but nothing a brown sign points at.

const TOURISM = {
  museum: 'museum',
  gallery: 'museum',
  attraction: 'attraction',
  zoo: 'attraction',
  theme_park: 'attraction',
  aquarium: 'attraction',
  viewpoint: 'viewpoint',
}

function categorise(t) {
  const title = (t.protection_title || '').toLowerCase()
  const cls = t.protect_class
  if (t.boundary === 'national_park' || title === 'national park' || cls === '2') return 'national_park'
  if (title === 'national monument') return 'heritage'
  if (
    t.leisure === 'nature_reserve' ||
    t.boundary === 'protected_area' ||
    /reserve|biosphere|marine park/.test(title)
  ) return 'nature_reserve'
  if (t.tourism && TOURISM[t.tourism]) return TOURISM[t.tourism]
  if (t.historic && HISTORIC[t.historic]) return HISTORIC[t.historic]
  return null
}

// ── Normalisation ─────────────────────────────────────────────────────────────
const pick = (t, ...keys) => {
  for (const k of keys) if (t[k] && t[k].trim()) return t[k].trim()
  return null
}

function normaliseName(s) {
  return s
    .normalize('NFKD')
    .replace(/[֑-ׇ]/g, '')       // Hebrew niqqud / cantillation
    .replace(/["'`׳״.,()\-–—]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
}

// Strip the category word out of a Hebrew name so "גן לאומי קיסריה" and
// "קיסריה" collapse onto one place.
const HE_PREFIXES = [
  'גן לאומי', 'שמורת טבע', 'שמורה', 'אתר לאומי', 'פארק לאומי', 'מוזיאון', 'תצפית',
  'חורבת', 'ח׳ ', 'תל ', 'מצודת', 'אנדרטת', 'אנדרטה ל',
]
function nameKey(he, en) {
  let base = he || en || ''
  for (const p of HE_PREFIXES) {
    if (base.startsWith(p)) { base = base.slice(p.length); break }
  }
  return normaliseName(base) || normaliseName(en || '')
}

function coordsOf(el) {
  if (typeof el.lat === 'number' && typeof el.lon === 'number') return [el.lat, el.lon]
  if (el.center) return [el.center.lat, el.center.lon]
  return null
}

function toSite(el) {
  const t = el.tags || {}
  const cat = categorise(t)
  if (!cat) return null

  const he = pick(t, 'name:he', 'name', 'alt_name:he')
  const en = pick(t, 'name:en', 'int_name', 'alt_name:en')
  if (!he && !en) return null                      // an unnamed feature gets no sign

  const c = coordsOf(el)
  if (!c) return null
  const [lat, lon] = c

  const site = {
    id: `${el.type[0]}${el.id}`,
    cat,
    he: he || en,
    lat: +lat.toFixed(6),
    lon: +lon.toFixed(6),
  }
  if (en && en !== site.he) site.en = en
  const ar = pick(t, 'name:ar')
  if (ar) site.ar = ar

  const desc = pick(t, 'description:he', 'description', 'description:en', 'inscription:he', 'inscription')
  if (desc && desc.length <= 600) site.desc = desc

  const wd = pick(t, 'wikidata')
  if (wd && /^Q\d+$/.test(wd)) site.wd = wd
  const wp = pick(t, 'wikipedia:he', 'wikipedia', 'wikipedia:en')
  if (wp) site.wp = wp.includes(':') ? wp : `he:${wp}`

  const url = pick(t, 'website', 'contact:website', 'url')
  if (url && /^https?:\/\//.test(url)) site.url = url
  const img = pick(t, 'image', 'image1')
  if (img && /^https?:\/\//.test(img)) site.img = img

  const ele = Number.parseFloat(t.ele)
  if (Number.isFinite(ele)) site.ele = Math.round(ele)

  const op = pick(t, 'operator:he', 'operator')
  if (op) site.op = op
  const oh = pick(t, 'opening_hours')
  if (oh && oh.length <= 200) site.oh = oh
  const fee = pick(t, 'fee')
  if (fee === 'yes' || fee === 'no') site.fee = fee === 'yes'

  return site
}

// Merge two records for the same place, keeping the better-ranked category and
// the union of the detail fields.
function merge(a, b) {
  const [keep, other] = RANK[a.cat] <= RANK[b.cat] ? [a, b] : [b, a]
  for (const k of ['en', 'ar', 'desc', 'wd', 'wp', 'url', 'img', 'ele', 'op', 'oh', 'fee']) {
    if (keep[k] === undefined && other[k] !== undefined) keep[k] = other[k]
  }
  // An area (way/relation) centre is a better "drive here" point than a stray node.
  if (keep.id[0] === 'n' && other.id[0] !== 'n') { keep.lat = other.lat; keep.lon = other.lon }
  return keep
}

// Two records are the same place if they share a wikidata id, or share a name
// and sit within ~600 m of each other.
function dedupe(sites) {
  const byWikidata = new Map()
  const byName = new Map()
  const out = []

  const cellsFor = (s) => {
    // 0.006° ≈ 600 m; check the 9 neighbouring cells so a pair straddling a
    // cell boundary still meets.
    const cells = []
    const gy = Math.round(s.lat / 0.006)
    const gx = Math.round(s.lon / 0.006)
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) cells.push(`${gy + dy}/${gx + dx}`)
    return cells
  }

  for (const s of sites) {
    if (s.wd && byWikidata.has(s.wd)) {
      const idx = byWikidata.get(s.wd)
      out[idx] = merge(out[idx], s)
      continue
    }
    const key = nameKey(s.he, s.en)
    let hit = -1
    if (key) {
      for (const cell of cellsFor(s)) {
        const idx = byName.get(`${key}@${cell}`)
        if (idx !== undefined) { hit = idx; break }
      }
    }
    if (hit >= 0) {
      out[hit] = merge(out[hit], s)
      if (out[hit].wd) byWikidata.set(out[hit].wd, hit)
      continue
    }
    const idx = out.push(s) - 1
    if (s.wd) byWikidata.set(s.wd, idx)
    if (key) byName.set(`${key}@${Math.round(s.lat / 0.006)}/${Math.round(s.lon / 0.006)}`, idx)
  }
  return out
}

// ── Main ──────────────────────────────────────────────────────────────────────
console.error('Fetching Overpass…')
const [poi, protectedAreas] = [
  await overpass('poi', QUERIES.poi),
  await overpass('protected', QUERIES.protected),
]

const raw = [...protectedAreas.elements, ...poi.elements]   // parks first: they win ties
const mapped = raw.map(toSite).filter(Boolean)
const sites = dedupe(mapped).sort((a, b) => a.he.localeCompare(b.he, 'he'))

const counts = {}
for (const s of sites) counts[s.cat] = (counts[s.cat] || 0) + 1

const body = { sites }
const version = createHash('sha256').update(JSON.stringify(body)).digest('hex').slice(0, 12)
const payload = {
  version,
  generatedAt: new Date().toISOString(),
  source: 'OpenStreetMap contributors (ODbL 1.0), via Overpass API',
  count: sites.length,
  counts,
  sites,
}

// A dataset this small means a query or a filter broke; writing it would ship an
// app with no content and no error.
const MIN_EXPECTED = 2000
if (sites.length < MIN_EXPECTED) {
  throw new Error(`only ${sites.length} sites (expected >= ${MIN_EXPECTED}) — refusing to write data/sites.json`)
}

const outFile = path.join(ROOT, 'data', 'sites.json')
await mkdir(path.dirname(outFile), { recursive: true })
await writeFile(outFile, JSON.stringify(payload))

// The phone ships with the dataset baked in, so the list is complete on first
// launch with no network; it refreshes from /api/sites when it can reach it.
const seed = path.join(ROOT, 'mobile', 'app', 'src', 'main', 'assets', 'sites.json')
await mkdir(path.dirname(seed), { recursive: true })
await writeFile(seed, JSON.stringify(payload))

console.error(`\n${sites.length} sites (from ${mapped.length} raw, ${raw.length} OSM features)`)
for (const c of CATEGORIES) console.error(`  ${c.padEnd(15)} ${counts[c] || 0}`)
console.error(`\nversion ${version} → ${path.relative(ROOT, outFile)}`)
