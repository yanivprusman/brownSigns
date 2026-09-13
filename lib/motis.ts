import { NextResponse } from 'next/server'

// Road routing and place search on the MOTIS instance this peer already runs for
// publicTransportation and midreshaze (street routing over the Israel OSM extract).
// One local HTTP call per request — no third-party map service — and a loud error
// when MOTIS is not configured, not reachable, or finds no road.

export type LatLon = { lat: number; lon: number }

/** A town, address or named place, as the destination picker lists it. */
export type Place = { name: string; area: string; lat: number; lon: number }

export type Route = {
  distanceM: number
  durationSec: number
  /** lat, lon, lat, lon, … — flat, so a long route stays a small payload. */
  path: number[]
}

/** A failure, with the HTTP status the phone should see. */
export class MotisError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

type Pt = [number, number]

const TIMEOUT_MS = 15_000
/** Metula to Eilat is about seven hours; no drive inside the country is longer. */
const MAX_DIRECT_TIME_SEC = 10 * 3600
/**
 * The phone measures every site against every segment of the road, so the road
 * is thinned first. 15 m cannot move a distance the list rounds to tens of
 * metres, and it takes a Tel Aviv–Eilat route from thousands of vertices to hundreds.
 */
const SIMPLIFY_TOLERANCE_M = 15
const METRES_PER_DEGREE = 111_320

type MotisArea = { name?: string; adminLevel?: number }
type MotisMatch = { name?: string; lat?: number; lon?: number; type?: string; areas?: MotisArea[] }
type MotisLeg = {
  mode?: string
  distance?: number
  duration?: number
  legGeometry?: { points?: string; precision?: number }
}
type MotisPlan = { direct?: { duration?: number; legs?: MotisLeg[] }[] }

export async function searchPlaces(text: string, limit = 8): Promise<Place[]> {
  const res = await motisFetch(`/api/v1/geocode?text=${encodeURIComponent(text)}&language=he`)
  const matches = (await res.json()) as MotisMatch[]
  if (!Array.isArray(matches)) {
    throw new MotisError('MOTIS geocode answered with something other than a list', 502)
  }

  const seen = new Set<string>()
  const places: Place[] = []
  for (const m of matches) {
    // Nobody drives to a bus stop, and stops share the names of the towns and
    // sites people do drive to — "מצדה" answers with four stops before anything else.
    if (m.type === 'STOP') continue
    if (!m.name || typeof m.lat !== 'number' || typeof m.lon !== 'number') continue
    // The town an address is in (for a town, its neighbourhood) — whatever
    // tells two matches of the same name apart.
    const area =
      (m.areas ?? [])
        .filter((a) => (a.adminLevel ?? 0) >= 8 && a.name && a.name !== m.name)
        .map((a) => a.name as string)[0] ?? ''
    const key = `${m.name}|${area}`
    if (seen.has(key)) continue
    seen.add(key)
    places.push({ name: m.name, area, lat: m.lat, lon: m.lon })
    if (places.length >= limit) break
  }
  return places
}

/** The fastest road by car. Throws MotisError(404) when no road connects the two points. */
export async function routeByCar(from: LatLon, to: LatLon): Promise<Route> {
  const res = await motisFetch(
    `/api/v1/plan?fromPlace=${from.lat},${from.lon}&toPlace=${to.lat},${to.lon}` +
      `&directModes=CAR&transitModes=&maxDirectTime=${MAX_DIRECT_TIME_SEC}`,
  )
  const plan = (await res.json()) as MotisPlan
  const direct = plan.direct?.[0]
  const legs = direct?.legs ?? []
  if (!direct || legs.length === 0 || legs.some((l) => l.mode !== 'CAR' || !l.legGeometry?.points)) {
    throw new MotisError('no drivable road between those points', 404)
  }

  const points: Pt[] = []
  for (const leg of legs) {
    const decoded = decodePolyline(leg.legGeometry!.points!, leg.legGeometry!.precision ?? 7)
    for (const p of points.length ? decoded.slice(1) : decoded) points.push(p)
  }
  const simplified = simplify(points, SIMPLIFY_TOLERANCE_M)
  if (simplified.length < 2) {
    throw new MotisError('the destination is where the trip starts', 422)
  }

  return {
    distanceM: Math.round(legs.reduce((sum, l) => sum + (l.distance ?? 0), 0)),
    durationSec: Math.round(direct.duration ?? legs.reduce((sum, l) => sum + (l.duration ?? 0), 0)),
    path: simplified.flatMap(([lat, lon]) => [round5(lat), round5(lon)]),
  }
}

/** "lat,lon" → a point, or null when it is not one. */
export function parsePoint(raw: string | null): LatLon | null {
  if (!raw) return null
  const parts = raw.split(',')
  if (parts.length !== 2 || parts.some((p) => p.trim() === '')) return null
  const [lat, lon] = parts.map(Number)
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null
  if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return null
  return { lat, lon }
}

/** A MotisError as the JSON answer the phone reads. Anything else is a bug and propagates. */
export function motisErrorResponse(e: unknown): NextResponse {
  if (e instanceof MotisError) {
    return NextResponse.json({ error: e.message }, { status: e.status })
  }
  throw e
}

function motisBase(): string {
  const url = process.env.MOTIS_URL
  if (!url) {
    throw new MotisError(
      'MOTIS_URL is not set — add it to .env.local (see .env.example; on the desktop it is http://127.0.0.1:3504)',
      500,
    )
  }
  return url.replace(/\/+$/, '')
}

async function motisFetch(pathAndQuery: string): Promise<Response> {
  const base = motisBase()
  let res: Response
  try {
    res = await fetch(base + pathAndQuery, { signal: AbortSignal.timeout(TIMEOUT_MS), cache: 'no-store' })
  } catch (e) {
    throw new MotisError(`MOTIS unreachable at ${base}: ${(e as Error).message}`, 502)
  }
  if (!res.ok) throw new MotisError(`MOTIS answered HTTP ${res.status}`, 502)
  return res
}

function decodePolyline(encoded: string, precision: number): Pt[] {
  const factor = 10 ** precision
  const out: Pt[] = []
  let index = 0
  let lat = 0
  let lon = 0
  while (index < encoded.length) {
    let shift = 0
    let result = 0
    let byte: number
    do {
      byte = encoded.charCodeAt(index++) - 63
      result |= (byte & 0x1f) << shift
      shift += 5
    } while (byte >= 0x20)
    lat += result & 1 ? ~(result >> 1) : result >> 1
    shift = 0
    result = 0
    do {
      byte = encoded.charCodeAt(index++) - 63
      result |= (byte & 0x1f) << shift
      shift += 5
    } while (byte >= 0x20)
    lon += result & 1 ? ~(result >> 1) : result >> 1
    out.push([lat / factor, lon / factor])
  }
  return out
}

/**
 * Douglas–Peucker, iterative (a long route would recurse too deep), in local
 * metres. The east-west scale is taken at the start of the route: anywhere in the
 * country it is right to a few percent, which is all a tolerance needs.
 */
function simplify(points: Pt[], toleranceM: number): Pt[] {
  const n = points.length
  if (n <= 2) return points
  const kx = Math.cos((points[0][0] * Math.PI) / 180) * METRES_PER_DEGREE
  const x = points.map(([, lon]) => lon * kx)
  const y = points.map(([lat]) => lat * METRES_PER_DEGREE)
  const keep = new Uint8Array(n)
  keep[0] = 1
  keep[n - 1] = 1
  const stack: [number, number][] = [[0, n - 1]]
  while (stack.length) {
    const [a, b] = stack.pop()!
    const dx = x[b] - x[a]
    const dy = y[b] - y[a]
    const len2 = dx * dx + dy * dy
    let worst = -1
    let worstD2 = toleranceM * toleranceM
    for (let i = a + 1; i < b; i++) {
      const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((x[i] - x[a]) * dx + (y[i] - y[a]) * dy) / len2))
      const ex = x[i] - (x[a] + t * dx)
      const ey = y[i] - (y[a] + t * dy)
      const d2 = ex * ex + ey * ey
      if (d2 > worstD2) {
        worst = i
        worstD2 = d2
      }
    }
    if (worst !== -1) {
      keep[worst] = 1
      stack.push([a, worst], [worst, b])
    }
  }
  return points.filter((_, i) => keep[i] === 1)
}

function round5(v: number): number {
  return Math.round(v * 1e5) / 1e5
}
