import styles from './Logo.module.css'

/** The HomeFlix mark: a house whose door is a play button. Original artwork (UX-02). */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <path
        d="M5 15.5 16 5.5l11 10V27H5z"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinejoin="round"
      />
      <path d="M13.5 14v9l7.5-4.5z" fill="currentColor" />
    </svg>
  )
}

export function Logo() {
  return (
    <span className={styles.logo}>
      <LogoMark className={styles.mark} />
      <span className={styles.wordmark}>
        Home<span className={styles.accent}>Flix</span>
      </span>
    </span>
  )
}
