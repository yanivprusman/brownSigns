import type { Category } from '@/lib/site-types'

/**
 * The pictogram a brown sign would carry for each kind of destination — drawn
 * rather than imported, so the page pulls no icon library for seven glyphs.
 */
const PATHS: Record<Category, string> = {
  // a tree
  national_park: 'M12 2 6 11h3l-4 6h5v5h4v-5h5l-4-6h3z',
  // two trees
  nature_reserve: 'M8 2 3.5 9H6l-3 4.5h4V18h2v-4.5h4L10 9h2.5zM16.5 6 13 11.5h2L12.5 15H15v3h3v-3h2.5L18 11.5h2z',
  // a museum front
  museum: 'M12 2 2 7v2h20V7zM4 11v7H2v2h20v-2h-2v-7h-2v7h-3v-7h-2v7h-3v-7z',
  // classical columns
  archaeology: 'M3 20h18v2H3zM5 8h2v10H5zm4 0h2v10H9zm4 0h2v10h-2zm4 0h2v10h-2zM12 1 2 6v2h20V6z',
  // a fortress
  heritage: 'M2 21h20V9h-3V6h-2v3h-3V6h-2v3H9V6H7v3H4v12zm5-7h4v5H7z',
  // a ferris wheel
  attraction: 'M12 2a9 9 0 1 0 4 17l1 3h-3l-2-5-2 5H7l1-3A9 9 0 0 0 12 2m0 3a6 6 0 1 1 0 12 6 6 0 0 1 0-12m0 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6',
  // mountains under a sky
  viewpoint: 'M3 19h18L14 8l-3.5 5.5L8.5 11zM6 4a2 2 0 1 1 0 4 2 2 0 0 1 0-4',
}

export function CategoryIcon({ cat, className }: { cat: Category; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden className={className}>
      <path d={PATHS[cat]} />
    </svg>
  )
}
