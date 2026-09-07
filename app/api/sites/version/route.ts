import { NextResponse } from 'next/server'
import { loadSites } from '@/lib/sites'

export const dynamic = 'force-dynamic'

/** Cheap freshness probe — the phone calls this on resume, not /api/sites. */
export async function GET() {
  const { version, generatedAt, count, counts } = await loadSites()
  return NextResponse.json(
    { version, generatedAt, count, counts },
    { headers: { 'Cache-Control': 'no-cache' } },
  )
}
