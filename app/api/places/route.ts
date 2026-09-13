import { NextRequest, NextResponse } from 'next/server'
import { motisErrorResponse, searchPlaces } from '@/lib/motis'

export const dynamic = 'force-dynamic'

/**
 * Towns, addresses and named places, for "where are you driving to" — `?q=`.
 * Under two letters matches half the country, so that answers with nothing
 * rather than asking MOTIS.
 */
export async function GET(req: NextRequest) {
  const q = (req.nextUrl.searchParams.get('q') ?? '').trim()
  if (q.length < 2) return NextResponse.json({ places: [] })
  try {
    return NextResponse.json({ places: await searchPlaces(q) }, { headers: { 'Cache-Control': 'no-store' } })
  } catch (e) {
    return motisErrorResponse(e)
  }
}
