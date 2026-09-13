import { NextRequest, NextResponse } from 'next/server'
import { parseLatLon, routeCar, simplifyPolyline } from '@automatelinux/geo'
import { ApiError, errorResponse, motisUrl } from '@/lib/api'

export const dynamic = 'force-dynamic'

/**
 * The phone measures every site against every segment of the road, so the road is
 * thinned first. 15 m cannot move a distance the list rounds to tens of metres, and
 * it takes Tel Aviv–Eilat from thousands of vertices to hundreds.
 */
const SIMPLIFY_TOLERANCE_M = 15

/**
 * The road by car from one point to another — `?from=lat,lon&to=lat,lon` — for
 * ordering the list by how close each site lies to it. The phone asks once per
 * destination and keeps the answer, so the order holds after the signal goes.
 */
export async function GET(req: NextRequest) {
  try {
    const from = parseLatLon(req.nextUrl.searchParams.get('from'))
    const to = parseLatLon(req.nextUrl.searchParams.get('to'))
    if (!from || !to) throw new ApiError('from and to must both be "lat,lon"', 400)

    const route = await routeCar(motisUrl(), from, to)
    const path = simplifyPolyline(route.points, SIMPLIFY_TOLERANCE_M)
    if (path.length < 2) throw new ApiError('the destination is where the trip starts', 422)

    return NextResponse.json(
      {
        distanceM: Math.round(route.distanceM),
        durationSec: Math.round(route.durationSec),
        // lat, lon, lat, lon, … — flat, so a long route stays a small payload.
        path: path.flatMap(([lat, lon]) => [round5(lat), round5(lon)]),
      },
      { headers: { 'Cache-Control': 'no-store' } },
    )
  } catch (e) {
    return errorResponse(e)
  }
}

function round5(v: number): number {
  return Math.round(v * 1e5) / 1e5
}
