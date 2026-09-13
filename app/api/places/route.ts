import { NextRequest, NextResponse } from 'next/server'
import { geocode, parseLatLon } from '@automatelinux/geo'
import { ApiError, errorResponse, motisUrl } from '@/lib/api'

export const dynamic = 'force-dynamic'

const LIMIT = 8

/**
 * Addresses, streets and towns for "where are you driving to" — `?q=`, with
 * `&lat=&lon=` so that equally good matches around the phone come first.
 *
 * The search is the one publicTransportation uses (@automatelinux/geo). All that is
 * decided here is what a driver picks from: no bus stops, and one row per name — the
 * search returns a long street once per segment.
 */
export async function GET(req: NextRequest) {
  try {
    const params = req.nextUrl.searchParams
    const q = (params.get('q') ?? '').trim()
    // Under two letters matches half the country.
    if (q.length < 2) return NextResponse.json({ places: [] })

    const lat = params.get('lat')
    const lon = params.get('lon')
    const near = lat === null && lon === null ? null : parseLatLon(`${lat},${lon}`)
    if ((lat !== null || lon !== null) && !near) throw new ApiError('lat and lon must both be numbers', 400)

    const results = await geocode(q, { motisUrl: motisUrl(), near })
    const seen = new Set<string>()
    const places: { name: string; lat: number; lon: number }[] = []
    for (const r of results) {
      if (r.type === 'STOP' || seen.has(r.name)) continue
      seen.add(r.name)
      places.push({ name: r.name, lat: r.lat, lon: r.lon })
      if (places.length === LIMIT) break
    }
    return NextResponse.json({ places }, { headers: { 'Cache-Control': 'no-store' } })
  } catch (e) {
    return errorResponse(e)
  }
}
