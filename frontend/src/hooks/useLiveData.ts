import { useCallback, useEffect, useState } from 'react'
import { isAbortError } from '../api/client'
import type { AsyncState } from './useAsync'

interface Result<T> {
  state: AsyncState<T>
  /** The last refresh failed: the data shown is the previous one. */
  stale: boolean
}

/**
 * Like useAsync, but the data stays on screen while it is refreshed, so a list does not flash. After each
 * successful load, `pollAfter` says in how many milliseconds to load again, or null to stop. A failed
 * refresh keeps the previous data and marks it stale; the next refresh is still made.
 * `load` and `pollAfter` must be memoized by the caller.
 */
export function useLiveData<T>(load: (signal: AbortSignal) => Promise<T>, pollAfter: (data: T) => number | null) {
  const [result, setResult] = useState<Result<T>>({ state: { status: 'loading' }, stale: false })
  const [request, setRequest] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setResult({ state: { status: 'success', data }, stale: false })
      },
      (error: unknown) => {
        if (controller.signal.aborted || isAbortError(error)) return
        setResult((previous) =>
          previous.state.status === 'success'
            ? { state: previous.state, stale: true }
            : { state: { status: 'error', error }, stale: false },
        )
      },
    )
    return () => controller.abort()
  }, [load, request])

  const delay = result.state.status === 'success' ? pollAfter(result.state.data) : null
  useEffect(() => {
    if (delay === null) return
    const timer = setTimeout(() => setRequest((value) => value + 1), delay)
    return () => clearTimeout(timer)
    // A new result restarts the wait, even with the same delay.
  }, [delay, result])

  /** Loads again now, keeping the current data on screen. */
  const refresh = useCallback(() => setRequest((value) => value + 1), [])

  /** After an error: shows the loading state and loads again. */
  const retry = useCallback(() => {
    setResult({ state: { status: 'loading' }, stale: false })
    setRequest((value) => value + 1)
  }, [])

  return { state: result.state, stale: result.stale, refresh, retry }
}
