import { NextRequest, NextResponse } from 'next/server'
import { loadSites } from '@/lib/sites'

export const dynamic = 'force-dynamic'

/**
 * The whole dataset (~1.2 MB). Clients cache it by `version` and re-fetch only
 * when /api/sites/version reports a different one; passing `?have=<version>`
 * short-circuits that round trip.
 */
export async function GET(req: NextRequest) {
  const data = await loadSites()
  const have = req.nextUrl.searchParams.get('have')
  if (have && have === data.version) {
    return NextResponse.json(
      { version: data.version, generatedAt: data.generatedAt, count: data.count, unchanged: true },
      { headers: { 'Cache-Control': 'no-cache' } },
    )
  }
  return NextResponse.json(data, {
    headers: { 'Cache-Control': 'public, max-age=3600', ETag: `"${data.version}"` },
  })
}
