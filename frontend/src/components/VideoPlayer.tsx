import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { probeStream, streamUrl } from '../api/library'
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
/** MediaError.MEDIA_ERR_NETWORK */
const MEDIA_ERR_NETWORK = 2

type Problem = 'missing' | 'denied' | 'unsupported' | 'network' | 'unavailable' | 'unknown'

const problems: Record<Problem, { title: string; text: string; canRetry: boolean }> = {
  missing: {
    title: 'Fichier introuvable',
    text: 'Le fichier de ce film est introuvable. Il a peut-être été déplacé ou supprimé de la bibliothèque.',
    canRetry: false,
  },
  denied: {
    title: 'Accès refusé',
    text: "Vous n'avez pas accès à cette vidéo.",
    canRetry: false,
  },
  unsupported: {
    title: 'Format non pris en charge',
    text: 'Ce navigateur ne peut pas lire ce fichier. HomeFlix lit les vidéos MP4 (vidéo H.264 et son AAC).',
    canRetry: false,
  },
  network: {
    title: 'Lecture interrompue',
    text: 'La connexion a été interrompue. Vérifiez le réseau puis réessayez.',
    canRetry: true,
  },
  unavailable: {
    title: 'Service indisponible',
    text: 'La bibliothèque est momentanément indisponible. Réessayez dans un instant.',
    canRetry: true,
  },
  unknown: {
    title: 'Lecture impossible',
    text: 'Une erreur est survenue pendant la lecture. Réessayez dans un instant.',
    canRetry: true,
  },
}

/** Finds out why the stream failed, since the <video> element does not say (PLY-05). */
async function diagnose(mediaId: string, error: MediaError | null): Promise<Problem | 'expired'> {
  const status = await probeStream(mediaId)
  if (status === 401) return 'expired'
  if (status === 403) return 'denied'
  if (status === 404 || status === 410) return 'missing'
  if (status === 0) return 'network'
  if (status === 502 || status === 503 || status === 504) return 'unavailable'
  if (status === 200 || status === 206) return error?.code === MEDIA_ERR_NETWORK ? 'network' : 'unsupported'
  return 'unknown'
}

type WebkitVideo = HTMLVideoElement & { webkitEnterFullscreen?: () => void }

function fullscreenSupported(): boolean {
  return Boolean(document.fullscreenEnabled) || 'webkitEnterFullscreen' in HTMLVideoElement.prototype
}

export function VideoPlayer({ mediaId, title, backTo }: { mediaId: string; title: string; backTo: string }) {
  const { expire } = useAuth()
  const containerRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const playButtonRef = useRef<HTMLButtonElement>(null)
  const idleTimer = useRef<number | undefined>(undefined)
  const resumeAt = useRef(0)

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
          setPlaying(true)
          wake()
        }}
        onPause={() => setPlaying(false)}
        onEnded={() => setPlaying(false)}
        onTimeUpdate={(event) => setTime(event.currentTarget.currentTime)}
        onLoadedMetadata={(event) => {
          setDuration(event.currentTarget.duration)
          if (resumeAt.current > 0) {
            event.currentTarget.currentTime = resumeAt.current
            resumeAt.current = 0
          }
        }}
        onDurationChange={(event) => setDuration(event.currentTarget.duration)}
        onWaiting={() => setLoading(true)}
        onCanPlay={() => setLoading(false)}
        onPlaying={() => setLoading(false)}
        onError={() => void handleError()}
      />

      <div className={`${styles.topBar} ${styles.chrome}`}>
        <ButtonLink to={backTo} variant="secondary" className={styles.back}>
          Retour
        </ButtonLink>
        <h1 className={styles.title}>{title}</h1>
      </div>

      {loading && !details && (
        <div className={styles.center} role="status">
          <span className={styles.spinner} aria-hidden="true" />
          <span className="visually-hidden">Chargement de la vidéo…</span>
        </div>
      )}

      {details && (
        <div className={styles.center}>
          <div className={styles.problem} role="alert">
            <h2 className={styles.problemTitle}>{details.title}</h2>
            <p>{details.text}</p>
            <div className={styles.problemActions}>
              {details.canRetry && <Button onClick={retry}>Réessayer</Button>}
              <ButtonLink to={backTo} variant="secondary">
                Retour au film
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
          aria-label="Position de lecture"
          aria-valuetext={`${formatSpokenTime(time)} sur ${formatSpokenTime(duration)}`}
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
            aria-label={playing ? 'Pause' : 'Lecture'}
            disabled={Boolean(problem)}
            onClick={togglePlay}
          >
            {playing ? <PauseIcon /> : <PlayIcon />}
          </button>
          <button
            type="button"
            className={styles.control}
            aria-label={`Reculer de ${SEEK_STEP} secondes`}
            disabled={!duration || Boolean(problem)}
            onClick={() => seekTo(time - SEEK_STEP)}
          >
            <BackTenIcon />
          </button>
          <button
            type="button"
            className={styles.control}
            aria-label={`Avancer de ${SEEK_STEP} secondes`}
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
              aria-label={muted ? 'Réactiver le son' : 'Couper le son'}
              onClick={toggleMute}
            >
              <VolumeIcon muted={shownVolume === 0} />
            </button>
            <input
              className={styles.volumeSlider}
              type="range"
              aria-label="Volume"
              aria-valuetext={`${Math.round(shownVolume * 100)} %`}
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
              aria-label={fullscreen ? 'Quitter le plein écran' : 'Plein écran'}
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
