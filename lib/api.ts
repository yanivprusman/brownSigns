import { NextResponse } from 'next/server'
import { RoutingError } from '@automatelinux/geo'

/** A request this backend refuses or cannot serve, with the HTTP status the phone should see. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

/** MOTIS's base URL from .env.local — an error naming the file when it is missing, never a guessed port. */
export function motisUrl(): string {
  const url = process.env.MOTIS_URL
  if (!url) {
    throw new ApiError('MOTIS_URL is not set — add it to .env.local (see .env.example; on the desktop http://127.0.0.1:3504)', 500)
  }
  return url
}

/** A known failure as the JSON answer the phone reads. Anything else is a bug and propagates. */
export function errorResponse(e: unknown): NextResponse {
  if (e instanceof ApiError || e instanceof RoutingError) {
    return NextResponse.json({ error: e.message }, { status: e.status })
  }
  throw e
}
