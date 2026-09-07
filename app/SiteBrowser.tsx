'use client'

import { useEffect, useMemo, useState } from 'react'
import type { Category, Site, SiteData } from '@/lib/site-types'
import { CATEGORY_HE, CATEGORY_ORDER, distanceMetres } from '@/lib/site-types'
import { CategoryIcon } from './CategoryIcon'

const CATEGORY_PLURAL: Record<Category, string> = {
  national_park: 'גנים לאומיים',
  nature_reserve: 'שמורות טבע',
  museum: 'מוזיאונים',
  archaeology: 'ארכיאולוגיה',
  heritage: 'מורשת',
  attraction: 'אטרקציות',
  viewpoint: 'תצפיות',
}

const COMPASS = ['צפון', 'צפון-מזרח', 'מזרח', 'דרום-מזרח', 'דרום', 'דרום-מערב', 'מערב', 'צפון-מערב']

/** Rendering every match at once is pointless when the list is ordered by distance. */
const PAGE = 300

type Origin = { lat: number; lon: number; accuracy?: number }

type Located =
  | { kind: 'pending' }
  | { kind: 'denied' }
  | { kind: 'searching' }
  | { kind: 'fixed'; at: Origin }

function bearing(from: Origin, to: Site) {
  const rad = (d: number) => (d * Math.PI) / 180
  const dLon = rad(to.lon - from.lon)
  const y = Math.sin(dLon) * Math.cos(rad(to.lat))
  const x =
    Math.cos(rad(from.lat)) * Math.sin(rad(to.lat)) -
    Math.sin(rad(from.lat)) * Math.cos(rad(to.lat)) * Math.cos(dLon)
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360
}

function formatDistance(metres: number): [string, string] {
  if (metres < 1000) return [String(Math.round(metres / 10) * 10), 'מ׳']
  if (metres < 100_000) {
    const km = Math.round(metres / 100) / 10
    return [Number.isInteger(km) ? String(km) : km.toFixed(1), 'ק״מ']
  }
  return [String(Math.round(metres / 1000)), 'ק״מ']
}

const normalise = (s: string) =>
  s
    .replace(/[֑-ׇ׳״"'`]/g, '')
    .replace(/[-–—]/g, ' ')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()

export function SiteBrowser() {
  const [data, setData] = useState<SiteData | null>(null)
  const [failed, setFailed] = useState<string | null>(null)
  const [located, setLocated] = useState<Located>({ kind: 'pending' })
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Set<Category>>(new Set())
  const [shown, setShown] = useState(PAGE)

  useEffect(() => {
    fetch('/api/sites')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then(setData)
      .catch((e: Error) => setFailed(e.message))
  }, [])

  useEffect(() => {
    if (!('geolocation' in navigator)) return
    setLocated({ kind: 'searching' })
    const id = navigator.geolocation.watchPosition(
      (pos) =>
        setLocated({
          kind: 'fixed',
          at: { lat: pos.coords.latitude, lon: pos.coords.longitude, accuracy: pos.coords.accuracy },
        }),
      () => setLocated({ kind: 'denied' }),
      { enableHighAccuracy: false, maximumAge: 30_000, timeout: 20_000 },
    )
    return () => navigator.geolocation.clearWatch(id)
  }, [])

  const origin = located.kind === 'fixed' ? located.at : null

  const matched = useMemo(() => {
    if (!data) return []
    const needle = normalise(query)
    return data.sites.filter(
      (s) =>
        (selected.size === 0 || selected.has(s.cat)) &&
        (needle === '' ||
          normalise(s.he).includes(needle) ||
          (s.en ? normalise(s.en).includes(needle) : false) ||
          (s.ar ? normalise(s.ar).includes(needle) : false)),
    )
  }, [data, query, selected])

  const ordered = useMemo(() => {
    if (!origin) {
      return [...matched]
        .sort((a, b) => normalise(a.he).localeCompare(normalise(b.he), 'he'))
        .map((site) => ({ site, metres: null as number | null }))
    }
    return matched
      .map((site) => ({ site, metres: distanceMetres(origin.lat, origin.lon, site.lat, site.lon) }))
      .sort((a, b) => a.metres! - b.metres!)
  }, [matched, origin])

  const counts = useMemo(() => {
    const out = {} as Record<Category, number>
    for (const s of matched) out[s.cat] = (out[s.cat] ?? 0) + 1
    return out
  }, [matched])

  useEffect(() => setShown(PAGE), [query, selected, origin?.lat, origin?.lon])

  const toggle = (cat: Category) =>
    setSelected((prev) => {
      const next = new Set(prev)
      if (!next.delete(cat)) next.add(cat)
      return next
    })

  return (
    <div className="mx-auto w-full max-w-2xl">
      <header className="bg-[var(--field)] px-3 pt-3 pb-3">
        <div className="rounded-[10px] border-2 border-white/85 px-4 py-3 text-white">
          <div className="flex items-baseline gap-3">
            <h1 className="text-3xl font-extrabold tracking-tight">שלט חום</h1>
            <span className="ms-auto text-[13px] font-bold text-white/70">
              {data ? `${data.count.toLocaleString('en-US')} יעדים` : ' '}
            </span>
          </div>
          <p className="mt-1 text-[13px] text-white/85">
            {located.kind === 'fixed'
              ? `לפי הקרוב אליך${located.at.accuracy ? ` · דיוק ${Math.round(located.at.accuracy)} מ׳` : ''}`
              : located.kind === 'searching'
                ? 'מחפש מיקום…'
                : located.kind === 'denied'
                  ? 'אין הרשאת מיקום — הרשימה לפי א״ב'
                  : 'הפעל מיקום כדי לסדר לפי הקרוב אליך'}
          </p>
        </div>
      </header>

      <div className="bg-[var(--panel)] pb-3">
        <div className="px-4 pt-3 pb-3">
          <input
            data-id="site-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="חפש יעד — מצדה, קיסריה, מוזיאון…"
            className="w-full rounded-[10px] border border-[var(--hairline)] bg-[var(--panel)] px-4 py-3 text-[15px] outline-none focus:border-[var(--field)]"
          />
        </div>
        <div className="flex gap-2 overflow-x-auto px-4 pb-1">
          <Chip
            label="הכול"
            count={matched.length}
            active={selected.size === 0}
            onClick={() => setSelected(new Set())}
          />
          {CATEGORY_ORDER.map((cat) => (
            <Chip
              key={cat}
              cat={cat}
              label={CATEGORY_PLURAL[cat]}
              count={counts[cat] ?? 0}
              active={selected.has(cat)}
              onClick={() => toggle(cat)}
            />
          ))}
        </div>
      </div>

      {failed && <Notice>הרשימה לא נטענה: {failed}</Notice>}
      {!failed && !data && <Notice>טוען…</Notice>}
      {data && ordered.length === 0 && (
        <Notice>
          {query ? `לא נמצא יעד בשם "${query}"` : 'אין יעדים בסינון הזה'}
        </Notice>
      )}

      <ul>
        {ordered.slice(0, shown).map(({ site, metres }) => (
          <li key={site.id} className="row-lazy border-b border-[var(--hairline)]">
            {/* The page opens the place on a map anyone's browser can show. A
                geo: URI would be better on a phone and inert on a laptop; the
                phone app is where "navigate there" belongs. */}
            <a
              data-id={`site-${site.id}`}
              href={`https://www.openstreetmap.org/?mlat=${site.lat}&mlon=${site.lon}#map=16/${site.lat}/${site.lon}`}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-3.5 px-4 py-3 transition-colors hover:bg-[var(--panel)]"
            >
              <span className="grid size-11 shrink-0 place-items-center rounded-[9px] bg-[var(--field)] p-1">
                <span className="grid size-full place-items-center rounded-[6px] border-[1.5px] border-white/90">
                  <CategoryIcon cat={site.cat} className="size-5 text-white" />
                </span>
              </span>

              <span className="min-w-0 flex-1">
                <span className="block truncate font-bold text-[var(--ink)]">{site.he}</span>
                <span className="block truncate text-[13px] text-[var(--ink-dim)]">
                  {CATEGORY_HE[site.cat]}
                  {site.en ? ` · ${site.en}` : ''}
                </span>
              </span>

              <span className="w-[86px] shrink-0 text-center">
                {metres === null ? (
                  <span className="text-[var(--ink-dim)]">—</span>
                ) : (
                  <>
                    <span className="flex items-baseline justify-center gap-1 text-[var(--measure)]">
                      <b className="text-[19px] font-extrabold tracking-tight">
                        {formatDistance(metres)[0]}
                      </b>
                      <span className="text-[11px]">{formatDistance(metres)[1]}</span>
                    </span>
                    {origin && (
                      <span className="block text-[11px] text-[var(--ink-dim)]">
                        {COMPASS[Math.round(bearing(origin, site) / 45) % 8]}
                      </span>
                    )}
                  </>
                )}
              </span>
            </a>
          </li>
        ))}
      </ul>

      {ordered.length > shown && (
        <button
          data-id="show-more"
          onClick={() => setShown((n) => n + PAGE)}
          className="mx-auto my-6 block cursor-pointer rounded-[9px] bg-[var(--field)] px-5 py-3 text-[15px] font-bold text-white transition-opacity hover:opacity-90"
        >
          עוד {Math.min(PAGE, ordered.length - shown).toLocaleString('en-US')} מתוך{' '}
          {(ordered.length - shown).toLocaleString('en-US')}
        </button>
      )}

      {data && (
        <p className="px-6 py-8 text-center text-[12px] text-[var(--ink-dim)]">
          {data.count.toLocaleString('en-US')} יעדים · {data.source}
        </p>
      )}
    </div>
  )
}

function Chip({
  cat,
  label,
  count,
  active,
  onClick,
}: {
  cat?: Category
  label: string
  count: number
  active: boolean
  onClick: () => void
}) {
  const disabled = count === 0 && !active
  return (
    <button
      data-id={`chip-${cat ?? 'all'}`}
      data-active-tab={active ? label : undefined}
      onClick={onClick}
      disabled={disabled}
      className={[
        'flex shrink-0 cursor-pointer items-center gap-1.5 rounded-[9px] border px-3 py-2 text-[14px] font-bold transition-colors',
        active
          ? 'border-[var(--color-sign-edge)] bg-[var(--field)] text-white'
          : 'border-[var(--hairline)] bg-[var(--panel)] text-[var(--ink)] hover:border-[var(--field)]',
        disabled && 'cursor-not-allowed opacity-50 hover:border-[var(--hairline)]',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {cat && <CategoryIcon cat={cat} className={active ? 'size-4 text-white' : 'size-4 text-[var(--field)]'} />}
      {label}
      <span className={active ? 'text-[12px] font-normal text-white/75' : 'text-[12px] font-normal text-[var(--ink-dim)]'}>
        {count.toLocaleString('en-US')}
      </span>
    </button>
  )
}

function Notice({ children }: { children: React.ReactNode }) {
  return <p className="px-6 py-16 text-center text-[15px] text-[var(--ink-dim)]">{children}</p>
}
