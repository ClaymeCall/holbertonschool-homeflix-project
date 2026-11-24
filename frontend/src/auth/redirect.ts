/** Where to go after signing in or choosing a profile: the protected page the user asked for, or home. */
export function redirectTarget(state: unknown): string {
  if (state && typeof state === 'object' && 'from' in state) {
    const from = state.from as { pathname?: unknown; search?: unknown }
    if (typeof from.pathname === 'string' && from.pathname.startsWith('/') && !from.pathname.startsWith('//')) {
      return from.pathname + (typeof from.search === 'string' ? from.search : '')
    }
  }
  return '/'
}
