import { ApiError } from './client'

/**
 * The text to show for an API failure (SEC-04, UX-05, ADR-0021). The server writes
 * it, so it is shown as it is. The interface writes its own text only where the
 * server cannot: no answer at all (status 0), and an answer that is not the
 * common error body.
 */
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Something unexpected went wrong. Please try again in a moment.'
  }
  // A 5xx other than 503 is an unexpected failure, so its body cannot be trusted to follow the format.
  // The server's text is shown for 4xx and for 503, which the contract defines (ADR-0021).
  const trusted = error.status < 500 || error.status === 503
  if (trusted && error.serverMessage) return error.serverMessage
  if (error.status === 0) return 'HomeFlix cannot be reached. Check your connection, then try again.'
  if (error.status === 503) return 'This service is temporarily unavailable. Please try again in a moment.'
  return 'Something went wrong on our side. Please try again in a moment.'
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
