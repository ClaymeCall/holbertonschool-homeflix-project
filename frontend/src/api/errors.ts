import { ApiError } from './client'

/** Plain-language message for an API failure, with no technical details (SEC-04, UX-05). */
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Something unexpected went wrong. Please try again in a moment.'
  }
  switch (error.status) {
    case 0:
      return 'HomeFlix cannot be reached. Check your connection, then try again.'
    case 400:
    case 422:
      return 'Some of the information is not valid. Check it, then try again.'
    case 401:
      return 'Your session has expired. Sign in again to continue.'
    case 403:
      return 'You do not have access to this page.'
    case 404:
      return 'This item cannot be found. It may have been removed.'
    case 503:
      return 'This service is temporarily unavailable. Please try again in a moment.'
    default:
      return 'Something went wrong on our side. Please try again in a moment.'
  }
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
