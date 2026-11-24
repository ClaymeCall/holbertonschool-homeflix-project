import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MediaDetails } from '../api/library'
import { callsTo, jsonResponse, mockApi, signedInRoutes, type Route } from '../test/http'
import { SAVE_EVERY_MS } from '../components/VideoPlayer'
import { renderApp } from '../test/render'

const movie: MediaDetails = {
  id: 'm1',
  title: 'Night of the Stars',
  description: null,
  posterUrl: null,
  genres: [],
  durationSeconds: 5520,
  availability: 'AVAILABLE',
}

/**
 * jsdom does not implement media playback: play, pause and the media
 * properties are replaced with a minimal fake that fires the same events.
 */
const fake = new WeakMap<HTMLMediaElement, { time: number; volume: number; muted: boolean }>()
function stateOf(media: HTMLMediaElement) {
  let state = fake.get(media)
  if (!state) {
    state = { time: 0, volume: 1, muted: false }
    fake.set(media, state)
  }
  return state
}

const originals = new Map<string, PropertyDescriptor | undefined>()
function override(name: string, descriptor: PropertyDescriptor) {
  originals.set(name, Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, name))
  Object.defineProperty(HTMLMediaElement.prototype, name, { configurable: true, ...descriptor })
}

let requestFullscreen: ReturnType<typeof vi.fn>

beforeEach(() => {
  override('play', {
    value(this: HTMLMediaElement) {
      this.dispatchEvent(new Event('play'))
      return Promise.resolve()
    },
  })
  override('pause', {
    value(this: HTMLMediaElement) {
      this.dispatchEvent(new Event('pause'))
    },
  })
  override('load', { value: () => undefined })
  override('currentTime', {
    get(this: HTMLMediaElement) {
      return stateOf(this).time
    },
    set(this: HTMLMediaElement, value: number) {
      stateOf(this).time = value
    },
  })
  override('volume', {
    get(this: HTMLMediaElement) {
      return stateOf(this).volume
    },
    set(this: HTMLMediaElement, value: number) {
      stateOf(this).volume = value
    },
  })
  override('muted', {
    get(this: HTMLMediaElement) {
      return stateOf(this).muted
    },
    set(this: HTMLMediaElement, value: boolean) {
      stateOf(this).muted = value
    },
  })
  override('duration', { get: () => 5520 })

  requestFullscreen = vi.fn(() => Promise.resolve())
  Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, value: true })
  Object.defineProperty(HTMLElement.prototype, 'requestFullscreen', { configurable: true, value: requestFullscreen })
})

afterEach(() => {
  for (const [name, descriptor] of originals) {
    if (descriptor) Object.defineProperty(HTMLMediaElement.prototype, name, descriptor)
    else Reflect.deleteProperty(HTMLMediaElement.prototype, name)
  }
  originals.clear()
  Reflect.deleteProperty(document, 'fullscreenEnabled')
  Reflect.deleteProperty(HTMLElement.prototype, 'requestFullscreen')
})

function player(stream: Route = () => new Response(null, { status: 206 }), details: Partial<MediaDetails> = {}) {
  return mockApi({
    ...signedInRoutes(),
    'GET /api/library/media/m1': () => jsonResponse({ ...movie, ...details }),
    'GET /api/library/media/m1/stream': stream,
  })
}

async function openPlayer() {
  renderApp('/watch/m1')
  const video = (await screen.findByLabelText(movie.title)) as HTMLVideoElement
  fireEvent.loadedMetadata(video)
  fireEvent.canPlay(video)
  return video
}

describe('PlayerPage', () => {
  it('plays the stream of the movie and shows its title', async () => {
    player()
    const video = await openPlayer()

    expect(video).toHaveAttribute('src', '/api/library/media/m1/stream')
    expect(video).toHaveAttribute('playsinline')
    expect(screen.getByRole('heading', { name: movie.title })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back' })).toHaveAttribute('href', '/movies/m1')
  })

  it('plays and pauses with the button, whose label follows the state', async () => {
    player()
    await openPlayer()

    await userEvent.click(screen.getByRole('button', { name: 'Play' }))
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Pause' }))
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
  })

  it('seeks with the position slider and the 10 second buttons', async () => {
    player()
    const video = await openPlayer()
    const position = screen.getByRole('slider', { name: 'Playback position' })
    expect(position).toHaveAttribute('max', '5520')

    fireEvent.change(position, { target: { value: '600' } })
    expect(video.currentTime).toBe(600)
    expect(position).toHaveAttribute('aria-valuetext', '10 minutes 0 seconds of 1 hour 32 minutes 0 seconds')

    await userEvent.click(screen.getByRole('button', { name: 'Forward 10 seconds' }))
    expect(video.currentTime).toBe(610)
    await userEvent.click(screen.getByRole('button', { name: 'Back 10 seconds' }))
    expect(video.currentTime).toBe(600)
  })

  it('changes and mutes the volume', async () => {
    player()
    const video = await openPlayer()

    fireEvent.change(screen.getByRole('slider', { name: 'Volume' }), { target: { value: '0.4' } })
    expect(video.volume).toBe(0.4)
    expect(screen.getByRole('slider', { name: 'Volume' })).toHaveAttribute('aria-valuetext', '40%')

    await userEvent.click(screen.getByRole('button', { name: 'Mute' }))
    expect(video.muted).toBe(true)
    expect(screen.getByRole('button', { name: 'Unmute' })).toBeInTheDocument()
  })

  it('goes fullscreen', async () => {
    player()
    await openPlayer()
    await userEvent.click(screen.getByRole('button', { name: 'Full screen' }))
    expect(requestFullscreen).toHaveBeenCalledTimes(1)
  })

  it('can be used with the keyboard only', async () => {
    player()
    const video = await openPlayer()
    const play = screen.getByRole('button', { name: 'Play' })
    expect(play).toHaveFocus()

    await userEvent.keyboard(' ')
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
    await userEvent.keyboard('k')
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()

    await userEvent.keyboard('{ArrowRight}{ArrowRight}')
    expect(video.currentTime).toBe(20)
    await userEvent.keyboard('{ArrowLeft}')
    expect(video.currentTime).toBe(10)

    await userEvent.keyboard('{ArrowDown}')
    expect(video.volume).toBe(0.9)
    await userEvent.keyboard('m')
    expect(video.muted).toBe(true)
    await userEvent.keyboard('f')
    expect(requestFullscreen).toHaveBeenCalledTimes(1)
  })

  it('shows a loading indicator until the video can play', async () => {
    player()
    renderApp('/watch/m1')
    await screen.findByLabelText(movie.title)
    expect(screen.getByRole('status')).toHaveTextContent('Loading the video')
  })

  it.each([
    [404, 'File not found', /moved or deleted/],
    [403, 'Access denied', /do not have access to this video/],
    [206, 'Format not supported', /MP4/],
    [415, 'Format not supported', /MP4/],
    [503, 'Service unavailable', /temporarily unavailable/],
  ])('explains a stream answering %i', async (status, title, text) => {
    player(() => new Response(null, { status }))
    const video = await openPlayer()
    fireEvent.error(video)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(title)
    expect(alert).toHaveTextContent(text)
    expect(screen.getByRole('link', { name: 'Back to the film' })).toHaveAttribute('href', '/movies/m1')
  })

  it('offers to retry after a temporary failure', async () => {
    player(() => new Response(null, { status: 503 }))
    const video = await openPlayer()
    fireEvent.error(video)

    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('sends the user to sign in when the session expired during playback', async () => {
    player(() => new Response(null, { status: 401 }))
    const video = await openPlayer()
    fireEvent.error(video)

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your session has expired')
  })

  it('does not start a movie that cannot be played', async () => {
    player(undefined, { availability: 'DOWNLOADING' })
    renderApp('/watch/m1')

    expect(await screen.findByRole('heading', { name: 'Cannot play' })).toBeInTheDocument()
    expect(screen.getByText(/is downloading/)).toBeInTheDocument()
    expect(screen.queryByLabelText(movie.title)).not.toBeInTheDocument()
  })

  it('opens from the play button of the movie page', async () => {
    player()
    renderApp('/movies/m1')
    await userEvent.click(await screen.findByRole('link', { name: 'Play' }))
    expect(await screen.findByLabelText(movie.title)).toHaveAttribute('src', '/api/library/media/m1/stream')
  })

  // ---- Resume and saving the position (I10; PLY-07, PLY-08) ----

  function playerWithProgress(progress: Route) {
    return mockApi({
      ...signedInRoutes(),
      'GET /api/library/media/m1': () => jsonResponse(movie),
      'GET /api/library/media/m1/stream': () => new Response(null, { status: 206 }),
      'GET /api/library/progress/m1': progress,
      'PUT /api/library/progress/m1': ({ init }) =>
        jsonResponse({ mediaId: 'm1', ...JSON.parse(String(init?.body)), state: 'STARTED', updatedAt: '2026-11-23T20:00:00Z' }),
    })
  }

  const saved = (positionSeconds: number, state: 'STARTED' | 'COMPLETED' = 'STARTED') => () =>
    jsonResponse({ mediaId: 'm1', positionSeconds, durationSeconds: 5520, state, updatedAt: '2026-11-22T20:00:00Z' })

  function savedBodies(fetch: ReturnType<typeof mockApi>) {
    return callsTo(fetch, 'PUT /api/library/progress/m1').map(([, init]) => JSON.parse(String(init?.body)))
  }

  function watchUntil(video: HTMLVideoElement, seconds: number) {
    video.currentTime = seconds
    fireEvent.timeUpdate(video)
  }

  it('starts a started film where the profile stopped', async () => {
    playerWithProgress(saved(600))
    const video = await openPlayer()
    expect(video.currentTime).toBe(600)
  })

  it('starts from the beginning when asked, without reading the progress', async () => {
    const fetch = playerWithProgress(saved(600))
    renderApp('/watch/m1?from=start')
    const video = (await screen.findByLabelText(movie.title)) as HTMLVideoElement
    fireEvent.loadedMetadata(video)

    expect(video.currentTime).toBe(0)
    expect(callsTo(fetch, 'GET /api/library/progress/m1')).toHaveLength(0)
  })

  it('starts a completed film from the beginning', async () => {
    playerWithProgress(saved(5400, 'COMPLETED'))
    const video = await openPlayer()
    expect(video.currentTime).toBe(0)
  })

  it('still plays when the progress cannot be read', async () => {
    playerWithProgress(() => jsonResponse({ error: 'service_unavailable', message: 'Unavailable.' }, 503))
    const video = await openPlayer()
    expect(video.currentTime).toBe(0)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('saves the position every 10 seconds while playing', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const fetch = playerWithProgress(saved(0, 'COMPLETED'))
      const video = await openPlayer()
      fireEvent.play(video)
      watchUntil(video, 125.7)

      await act(() => vi.advanceTimersByTimeAsync(SAVE_EVERY_MS))

      expect(savedBodies(fetch)).toEqual([{ positionSeconds: 125, durationSeconds: 5520 }])
    } finally {
      vi.useRealTimers()
    }
  })

  it('saves the position on pause and when leaving the player', async () => {
    const fetch = playerWithProgress(saved(0, 'COMPLETED'))
    const video = await openPlayer()
    fireEvent.play(video)
    watchUntil(video, 300)
    fireEvent.pause(video)
    expect(savedBodies(fetch)).toEqual([{ positionSeconds: 300, durationSeconds: 5520 }])

    watchUntil(video, 320)
    await userEvent.click(screen.getByRole('link', { name: 'Back' }))

    await waitFor(() => expect(savedBodies(fetch)).toHaveLength(2))
    expect(savedBodies(fetch)[1]).toEqual({ positionSeconds: 320, durationSeconds: 5520 })
  })

  it('saves the end of the film, so it counts as completed', async () => {
    const fetch = playerWithProgress(saved(0, 'COMPLETED'))
    const video = await openPlayer()
    fireEvent.play(video)
    watchUntil(video, 5520)
    fireEvent.ended(video)

    expect(savedBodies(fetch)).toEqual([{ positionSeconds: 5520, durationSeconds: 5520 }])
  })

  it('saves nothing when the film was never played', async () => {
    const fetch = playerWithProgress(saved(0, 'COMPLETED'))
    await openPlayer()
    await userEvent.click(screen.getByRole('link', { name: 'Back' }))

    expect(await screen.findByRole('heading', { level: 1, name: movie.title })).toBeInTheDocument()
    expect(savedBodies(fetch)).toHaveLength(0)
  })

  it('saves the new position when paused right after a jump', async () => {
    const fetch = playerWithProgress(saved(0, 'COMPLETED'))
    const video = await openPlayer()
    fireEvent.play(video)
    watchUntil(video, 120)

    fireEvent.change(screen.getByRole('slider', { name: 'Playback position' }), { target: { value: '600' } })
    fireEvent.pause(video)

    expect(savedBodies(fetch)).toEqual([{ positionSeconds: 600, durationSeconds: 5520 }])
  })

  it('sends one save at a time, so a slow save never overwrites a newer one', async () => {
    let answerFirst: () => void = () => {}
    const fetch = playerWithProgress(saved(0, 'COMPLETED'))
    const fast = fetch.getMockImplementation()!
    fetch.mockImplementation((input, init) =>
      init?.method === 'PUT' && callsTo(fetch, 'PUT /api/library/progress/m1').length === 1
        ? new Promise((resolve) => (answerFirst = () => resolve(jsonResponse({}))))
        : fast(input, init),
    )
    const video = await openPlayer()
    fireEvent.play(video)
    watchUntil(video, 120)
    fireEvent.pause(video)
    fireEvent.play(video)
    watchUntil(video, 150)
    fireEvent.pause(video)

    // The second save waits for the first one.
    expect(savedBodies(fetch)).toEqual([{ positionSeconds: 120, durationSeconds: 5520 }])
    answerFirst()
    await waitFor(() => expect(savedBodies(fetch)).toHaveLength(2))
    expect(savedBodies(fetch)[1]).toEqual({ positionSeconds: 150, durationSeconds: 5520 })
  })

  it('keeps the save sent when the tab closes, even if an older save was still on its way', async () => {
    // The server keeps the last save that reaches it. The first save is slow to arrive; the save sent on
    // closing the tab arrives at once.
    let kept: unknown = null
    let deliverFirst: () => void = () => {}
    const fetch = playerWithProgress(saved(0, 'COMPLETED'))
    const fast = fetch.getMockImplementation()!
    fetch.mockImplementation((input, init) => {
      if (init?.method !== 'PUT') return fast(input, init)
      const body = JSON.parse(String(init.body))
      if (callsTo(fetch, 'PUT /api/library/progress/m1').length > 1) {
        kept = body
        return fast(input, init)
      }
      return new Promise((resolve, reject) => {
        deliverFirst = () => {
          // A cancelled request never reaches the server.
          if (init.signal?.aborted) return
          kept = body
          resolve(jsonResponse({}))
        }
        init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      })
    })
    const video = await openPlayer()
    fireEvent.play(video)
    watchUntil(video, 120)
    fireEvent.pause(video)
    fireEvent.play(video)
    watchUntil(video, 150)

    act(() => {
      window.dispatchEvent(new Event('pagehide'))
    })
    deliverFirst()

    const sent = callsTo(fetch, 'PUT /api/library/progress/m1')
    expect(sent).toHaveLength(2)
    expect(sent[1][1]?.keepalive).toBe(true)
    await waitFor(() => expect(kept).toEqual({ positionSeconds: 150, durationSeconds: 5520 }))
    // The older save was cancelled, so it never reached the server after the last one.
    expect(sent[0][1]?.signal?.aborted).toBe(true)
  })
})
