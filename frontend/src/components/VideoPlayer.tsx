import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { probeStream, saveProgress, streamUrl } from '../api/library'
import { useAuth } from '../auth/AuthContext'
import { formatClock, formatSpokenTime } from '../format'
import { Button, ButtonLink } from './Button'
import {
  BackTenIcon,
  ForwardTenIcon,
  FullscreenIcon,
  PauseIcon,
  PlayIcon,
  VolumeIcon,
} from './PlayerIcons'
import styles from './VideoPlayer.module.css'

const SEEK_STEP = 10
const VOLUME_STEP = 0.1
const HIDE_CONTROLS_AFTER_MS = 3000
/** How often the position is saved while playing (PLY-07). Also saved on pause, at the end and on leaving. */
export const SAVE_EVERY_MS = 10_000
/** MediaError.MEDIA_ERR_NETWORK */
const MEDIA_ERR_NETWORK = 2

type Problem = 'missing' | 'denied' | 'unsupported' | 'network' | 'unavailable' | 'unknown'

const problems: Record<Problem, { title: string; text: string; canRetry: boolean }> = {
  missing: {
    title: 'File not found',
    text: 'The file for this film cannot be found. It may have been moved or deleted from the library.',
    canRetry: false,
  },
  denied: {
    title: 'Access denied',
    text: 'You do not have access to this video.',
    canRetry: false,
  },
  unsupported: {
    title: 'Format not supported',
    text: 'This browser cannot play this file. HomeFlix plays MP4 videos (H.264 video and AAC audio).',
    canRetry: false,
  },
  network: {
    title: 'Playback interrupted',
    text: 'The connection was interrupted. Check the network, then try again.',
    canRetry: true,
  },
  unavailable: {
    title: 'Service unavailable',
    text: 'The library is temporarily unavailable. Please try again in a moment.',
    canRetry: true,
  },
  unknown: {
    title: 'Cannot play',
    text: 'Something went wrong during playback. Please try again in a moment.',
    canRetry: true,
  },
}

/** Finds out why the stream failed, since the <video> element does not say (PLY-05). */
async function diagnose(mediaId: string, error: MediaError | null): Promise<Problem | 'expired'> {
  const status = await probeStream(mediaId)
  if (status === 401) return 'expired'
  if (status === 403) return 'denied'
  if (status === 404 || status === 410) return 'missing'
  if (status === 415) return 'unsupported'
  if (status === 0) return 'network'
  if (status === 502 || status === 503 || status === 504) return 'unavailable'
  if (status === 200 || status === 206) return error?.code === MEDIA_ERR_NETWORK ? 'network' : 'unsupported'
  return 'unknown'
}

type WebkitVideo = HTMLVideoElement & { webkitEnterFullscreen?: () => void }

interface Position {
  position: number
  duration: number
}

/**
 * Sends one save at a time, and then only the latest position asked for meanwhile: the server keeps the last
 * save it receives, so a slow save must never land after a newer one. A save while the page closes is sent at
 * once with keepalive, since there is no time to wait.
 */
function oneSaveAtATime(send: (saved: Position, keepalive: boolean) => Promise<unknown>) {
  let running = false
  let next: Position | null = null
  function run(saved: Position) {
    running = true
    send(saved, false)
      .catch(() => undefined)
      .finally(() => {
        running = false
        const queued = next
        next = null
        if (queued) run(queued)
      })
  }
  return (saved: Position, keepalive: boolean) => {
    if (keepalive) {
      next = null
      send(saved, true).catch(() => undefined)
    } else if (running) {
      next = saved
    } else {
      run(saved)
    }
  }
}

function fullscreenSupported(): boolean {
  return Boolean(document.fullscreenEnabled) || 'webkitEnterFullscreen' in HTMLVideoElement.prototype
}

export function VideoPlayer({
  mediaId,
  title,
  backTo,
  startAt = 0,
}: {
  mediaId: string
  title: string
  backTo: string
  /** Where to start, in seconds: the saved position when resuming (I10). */
  startAt?: number
}) {
  const { expire } = useAuth()
  const containerRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const playButtonRef = useRef<HTMLButtonElement>(null)
  const idleTimer = useRef<number | undefined>(undefined)
  const resumeAt = useRef(startAt)
  // The last position and duration seen, kept outside the element so they can still be saved on leaving.
  const watched = useRef({ played: false, position: 0, duration: 0 })

  const [playing, setPlaying] = useState(false)
  const [time, setTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [volume, setVolume] = useState(1)
  const [muted, setMuted] = useState(false)
  const [loading, setLoading] = useState(true)
  const [problem, setProblem] = useState<Problem | null>(null)
  const [fullscreen, setFullscreen] = useState(false)
  const [idle, setIdle] = useState(false)
  const [controlsFocused, setControlsFocused] = useState(false)
  const [canFullscreen] = useState(fullscreenSupported)

  /** Saves where the viewer is, for this profile (PLY-07). A failed save never interrupts playback. */
  const send = useMemo(
    () => oneSaveAtATime((saved, keepalive) => saveProgress(mediaId, saved.position, saved.duration, { keepalive })),
    [mediaId],
  )
  const save = useCallback(
    (keepalive = false) => {
      const { played, position, duration } = watched.current
      const whole = Math.floor(duration)
      if (!played || !Number.isFinite(whole) || whole < 1) return
      send({ position: Math.min(Math.floor(position), whole), duration: whole }, keepalive)
    },
    [send],
  )

  useEffect(() => {
    if (!playing) return
    const timer = window.setInterval(() => save(), SAVE_EVERY_MS)
    return () => window.clearInterval(timer)
  }, [playing, save])

  // On leaving the player, and when the tab is closed (keepalive lets that last request finish).
  useEffect(() => {
    const onPageHide = () => save(true)
    window.addEventListener('pagehide', onPageHide)
    return () => {
      window.removeEventListener('pagehide', onPageHide)
      save()
    }
  }, [save])

  useEffect(() => {
    playButtonRef.current?.focus()
    const onChange = () => setFullscreen(document.fullscreenElement === containerRef.current)
    document.addEventListener('fullscreenchange', onChange)
    return () => {
      document.removeEventListener('fullscreenchange', onChange)
      window.clearTimeout(idleTimer.current)
    }
  }, [])

  /** Shows the controls, and hides them again after a while without activity. */
  function wake() {
    setIdle(false)
    window.clearTimeout(idleTimer.current)
    idleTimer.current = window.setTimeout(() => setIdle(true), HIDE_CONTROLS_AFTER_MS)
  }

  function togglePlay() {
    const video = videoRef.current
    if (!video || problem) return
    if (playing) {
      video.pause()
    } else {
      video.play()?.catch(() => setPlaying(false))
    }
  }

  function seekTo(seconds: number) {
    const video = videoRef.current
    if (!video) return
    const target = Math.min(Math.max(seconds, 0), duration || 0)
    video.currentTime = target
    // Kept at once: a pause right after the jump must save the new position, not the last one played.
    watched.current.position = target
    setTime(target)
  }

  function changeVolume(value: number) {
    const video = videoRef.current
    if (!video) return
    const next = Math.round(Math.min(Math.max(value, 0), 1) * 100) / 100
    video.volume = next
    video.muted = next === 0
    setVolume(next)
    setMuted(next === 0)
  }

  function toggleMute() {
    if (muted && volume === 0) return changeVolume(0.5)
    const video = videoRef.current
    if (!video) return
    video.muted = !muted
    setMuted(!muted)
  }

  function toggleFullscreen() {
    const container = containerRef.current
    const video = videoRef.current as WebkitVideo | null
    if (document.fullscreenElement) {
      void document.exitFullscreen()
    } else if (document.fullscreenEnabled && container) {
      container.requestFullscreen().catch(() => undefined)
    } else {
      // iPhone Safari only allows the video element itself to go fullscreen.
      video?.webkitEnterFullscreen?.()
    }
  }

  async function handleError() {
    const video = videoRef.current
    setLoading(false)
    setPlaying(false)
    const found = await diagnose(mediaId, video?.error ?? null)
    if (found === 'expired') {
      expire()
    } else {
      setProblem(found)
    }
  }

  function retry() {
    const video = videoRef.current
    if (!video) return
    resumeAt.current = time
    setProblem(null)
    setLoading(true)
    video.load()
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    wake()
    if (problem || event.ctrlKey || event.metaKey || event.altKey) return
    const target = event.target as HTMLElement
    const onSlider = target instanceof HTMLInputElement && target.type === 'range'
    const onControl = target instanceof HTMLButtonElement || target instanceof HTMLAnchorElement

    switch (event.key) {
      case ' ':
        if (onControl) return
        togglePlay()
        break
      case 'k':
      case 'K':
        togglePlay()
        break
      case 'ArrowLeft':
        if (onSlider) return
        seekTo(time - SEEK_STEP)
        break
      case 'ArrowRight':
        if (onSlider) return
        seekTo(time + SEEK_STEP)
        break
      case 'ArrowUp':
        if (onSlider) return
        changeVolume(volume + VOLUME_STEP)
        break
      case 'ArrowDown':
        if (onSlider) return
        changeVolume(volume - VOLUME_STEP)
        break
      case 'm':
      case 'M':
        toggleMute()
        break
      case 'f':
      case 'F':
        if (canFullscreen) toggleFullscreen()
        break
      default:
        return
    }
    event.preventDefault()
  }

  const controlsHidden = playing && idle && !controlsFocused && !problem
  const shownVolume = muted ? 0 : volume
  const details = problem ? problems[problem] : null

  return (
    <div
      ref={containerRef}
      className={`${styles.player} ${controlsHidden ? styles.idle : ''}`}
      onKeyDown={handleKeyDown}
      onPointerMove={wake}
      onTouchStart={wake}
    >
      <video
        ref={videoRef}
        className={styles.video}
        src={streamUrl(mediaId)}
        aria-label={title}
        autoPlay
        playsInline
        preload="metadata"
        onClick={togglePlay}
        onPlay={() => {
          watched.current.played = true
          setPlaying(true)
          wake()
        }}
        onPause={() => {
          setPlaying(false)
          save()
        }}
        onEnded={() => {
          setPlaying(false)
          save()
        }}
        onTimeUpdate={(event) => {
          watched.current.position = event.currentTarget.currentTime
          setTime(event.currentTarget.currentTime)
        }}
        onLoadedMetadata={(event) => {
          watched.current.duration = event.currentTarget.duration
          setDuration(event.currentTarget.duration)
          if (resumeAt.current > 0) {
            event.currentTarget.currentTime = resumeAt.current
            resumeAt.current = 0
          }
        }}
        onDurationChange={(event) => {
          watched.current.duration = event.currentTarget.duration
          setDuration(event.currentTarget.duration)
        }}
        onWaiting={() => setLoading(true)}
        onCanPlay={() => setLoading(false)}
        onPlaying={() => setLoading(false)}
        onError={() => void handleError()}
      />

      <div className={`${styles.topBar} ${styles.chrome}`}>
        <ButtonLink to={backTo} variant="secondary" className={styles.back}>
          Back
        </ButtonLink>
        <h1 className={styles.title}>{title}</h1>
      </div>

      {loading && !details && (
        <div className={styles.center} role="status">
          <span className={styles.spinner} aria-hidden="true" />
          <span className="visually-hidden">Loading the video…</span>
        </div>
      )}

      {details && (
        <div className={styles.center}>
          <div className={styles.problem} role="alert">
            <h2 className={styles.problemTitle}>{details.title}</h2>
            <p>{details.text}</p>
            <div className={styles.problemActions}>
              {details.canRetry && <Button onClick={retry}>Try again</Button>}
              <ButtonLink to={backTo} variant="secondary">
                Back to the film
              </ButtonLink>
            </div>
          </div>
        </div>
      )}

      <div
        className={`${styles.controls} ${styles.chrome}`}
        onFocus={() => setControlsFocused(true)}
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) setControlsFocused(false)
        }}
      >
        <input
          className={styles.seek}
          type="range"
          aria-label="Playback position"
          aria-valuetext={`${formatSpokenTime(time)} of ${formatSpokenTime(duration)}`}
          min={0}
          max={duration || 0}
          step={1}
          value={Math.min(time, duration || 0)}
          disabled={!duration || Boolean(problem)}
          onChange={(event) => seekTo(Number(event.target.value))}
        />

        <div className={styles.bar}>
          <button
            ref={playButtonRef}
            type="button"
            className={`${styles.control} ${styles.primary}`}
            aria-label={playing ? 'Pause' : 'Play'}
            disabled={Boolean(problem)}
            onClick={togglePlay}
          >
            {playing ? <PauseIcon /> : <PlayIcon />}
          </button>
          <button
            type="button"
            className={styles.control}
            aria-label={`Back ${SEEK_STEP} seconds`}
            disabled={!duration || Boolean(problem)}
            onClick={() => seekTo(time - SEEK_STEP)}
          >
            <BackTenIcon />
          </button>
          <button
            type="button"
            className={styles.control}
            aria-label={`Forward ${SEEK_STEP} seconds`}
            disabled={!duration || Boolean(problem)}
            onClick={() => seekTo(time + SEEK_STEP)}
          >
            <ForwardTenIcon />
          </button>

          <span className={styles.time} aria-hidden="true">
            {formatClock(time)} / {formatClock(duration)}
          </span>

          <div className={styles.volume}>
            <button
              type="button"
              className={styles.control}
              aria-label={muted ? 'Unmute' : 'Mute'}
              onClick={toggleMute}
            >
              <VolumeIcon muted={shownVolume === 0} />
            </button>
            <input
              className={styles.volumeSlider}
              type="range"
              aria-label="Volume"
              aria-valuetext={`${Math.round(shownVolume * 100)}%`}
              min={0}
              max={1}
              step={0.05}
              value={shownVolume}
              onChange={(event) => changeVolume(Number(event.target.value))}
            />
          </div>

          {canFullscreen && (
            <button
              type="button"
              className={`${styles.control} ${styles.fullscreen}`}
              aria-label={fullscreen ? 'Exit full screen' : 'Full screen'}
              onClick={toggleFullscreen}
            >
              <FullscreenIcon active={fullscreen} />
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
