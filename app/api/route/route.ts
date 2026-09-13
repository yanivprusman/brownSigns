import { NextRequest, NextResponse } from 'next/server'
import { motisErrorResponse, parsePoint, routeByCar } from '@/lib/motis'

export const dynamic = 'force-dynamic'

/**
 * The road by car from one point to another — `?from=lat,lon&to=lat,lon` — for
 * ordering the list by how close each site lies to it. The phone asks once per
 * destination and keeps the answer, so the order holds after the signal goes.
 */
export async function GET(req: NextRequest) {
  const from = parsePoint(req.nextUrl.searchParams.get('from'))
  const to = parsePoint(req.nextUrl.searchParams.get('to'))
  if (!from || !to) {
    return NextResponse.json({ error: 'from and to must both be "lat,lon"' }, { status: 400 })
  }
  try {
    return NextResponse.json(await routeByCar(from, to), { headers: { 'Cache-Control': 'no-store' } })
  } catch (e) {
    return motisErrorResponse(e)
  }
}
