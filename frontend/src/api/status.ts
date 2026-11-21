/**
 * Service status for the administration area (K1, ADR-0024).
 *
 * The gateway does not route the services' health endpoints (ADR-0017), so each service is checked by
 * calling one of its own read-only administrator operations. An answer means the service and its database
 * work; a 5xx or no answer means that part of HomeFlix is unavailable. The torrent client is not checked:
 * a download that fails says why on the Downloads page.
 */
import { ApiError, apiRequest, isAbortError } from './client'

export type ServiceName = 'identity' | 'library' | 'acquisition'

export type ServiceCheck =
  | { service: ServiceName; status: 'running' }
  | { service: ServiceName; status: 'unavailable' }
  /** Any other answer, such as a 403 after the account lost its administrator role. */
  | { service: ServiceName; status: 'unknown'; error: unknown }

/** One cheap administrator GET of each service, documented in its contract (docs/api/). */
const PROBES: Record<ServiceName, string> = {
  identity: '/api/identity/accounts?limit=1',
  library: '/api/library/roots',
  acquisition: '/api/acquisition/entries',
}

export const SERVICES: readonly ServiceName[] = ['identity', 'library', 'acquisition']

async function check(service: ServiceName, signal?: AbortSignal): Promise<ServiceCheck> {
  try {
    await apiRequest<unknown>(PROBES[service], { signal })
    return { service, status: 'running' }
  } catch (error) {
    if (isAbortError(error)) throw error
    if (error instanceof ApiError && (error.status === 0 || error.status >= 500)) {
      return { service, status: 'unavailable' }
    }
    return { service, status: 'unknown', error }
  }
}

/** Checks the three services at the same time. Never fails: each result says how its service answered. */
export function checkServices(signal?: AbortSignal): Promise<ServiceCheck[]> {
  return Promise.all(SERVICES.map((service) => check(service, signal)))
}
