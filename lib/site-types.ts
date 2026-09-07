// Types and constants shared by the server and the browser. Kept apart from
// lib/sites.ts because that one reads the dataset off disk, and a client
// component importing it drags node:fs into the browser bundle.

/** A destination a brown road sign points at. */
export type Site = {
  /** OSM type-letter + id, e.g. "w1234567". Stable across dataset rebuilds. */
  id: string
  cat: Category
  /** Hebrew name (falls back to the English one when OSM has no Hebrew). */
  he: string
  en?: string
  ar?: string
  lat: number
  lon: number
  /** Metres above sea level, when OSM records it. */
  ele?: number
  desc?: string
  /** Wikipedia article as "<lang>:<title>". */
  wp?: string
  wd?: string
  url?: string
  img?: string
  op?: string
  oh?: string
  fee?: boolean
}

export type Category =
  | 'national_park'
  | 'nature_reserve'
  | 'museum'
  | 'archaeology'
  | 'heritage'
  | 'attraction'
  | 'viewpoint'

export type SiteData = {
  version: string
  generatedAt: string
  source: string
  count: number
  counts: Record<Category, number>
  sites: Site[]
}

export const CATEGORY_ORDER: Category[] = [
  'national_park',
  'nature_reserve',
  'museum',
  'archaeology',
  'heritage',
  'attraction',
  'viewpoint',
]

export const CATEGORY_HE: Record<Category, string> = {
  national_park: 'גן לאומי',
  nature_reserve: 'שמורת טבע',
  museum: 'מוזיאון',
  archaeology: 'אתר ארכיאולוגי',
  heritage: 'אתר מורשת',
  attraction: 'אתר תיירות',
  viewpoint: 'תצפית',
}

const EARTH_RADIUS_M = 6_371_000
const toRad = (deg: number) => (deg * Math.PI) / 180

/** Great-circle distance in metres. */
export function distanceMetres(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const dLat = toRad(bLat - aLat)
  const dLon = toRad(bLon - aLon)
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(s))
}
