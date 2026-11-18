import { useCallback, useEffect, useState } from 'react'
import { isAbortError } from '../api/client'

export type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'success'; data: T }

interface Settled<T> {
  load: (signal: AbortSignal) => Promise<T>
  attempt: number
  state: AsyncState<T>
}

/**
 * Runs `load` when it changes and exposes loading, error and success states.
 * `load` must be memoized by the caller (useCallback). The previous request is
 * aborted when the component unmounts or `load` changes.
 */
export function useAsync<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setSettled({ load, attempt, state: { status: 'success', data } })
      },
      (error: unknown) => {
        if (!controller.signal.aborted && !isAbortError(error)) {
          setSettled({ load, attempt, state: { status: 'error', error } })
        }
      },
    )
    return () => controller.abort()
  }, [load, attempt])

  const reload = useCallback(() => setAttempt((value) => value + 1), [])

  // A result only counts for the request that produced it; anything else is still loading.
  const state: AsyncState<T> =
    settled && settled.load === load && settled.attempt === attempt ? settled.state : { status: 'loading' }

  return { state, reload }
}
