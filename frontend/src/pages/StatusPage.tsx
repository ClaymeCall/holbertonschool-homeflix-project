import { describeError } from '../api/errors'
import { checkServices, type ServiceCheck, type ServiceName } from '../api/status'
import { Button } from '../components/Button'
import { LoadingState } from '../components/StatusMessage'
import { useAsync } from '../hooks/useAsync'
import styles from './AdminPage.module.css'

const SERVICE_LABEL: Record<ServiceName, string> = {
  identity: 'Accounts and sign-in',
  library: 'Library and playback',
  acquisition: 'Downloads',
}

/** What stops working while a service is unavailable, in the words of the household. */
const UNAVAILABLE_EFFECT: Record<ServiceName, string> = {
  identity: 'Nobody can sign in, and accounts and profiles cannot be changed.',
  library: 'Titles cannot be browsed or played, and the library cannot be scanned.',
  acquisition: 'Downloads cannot be started, cancelled or followed.',
}

const STATUS_LABEL: Record<ServiceCheck['status'], string> = {
  running: 'Working',
  unavailable: 'Unavailable',
  unknown: 'Not checked',
}

/** The state of each part of HomeFlix in plain language (K1, ADR-0024). */
export function StatusPage() {
  const checks = useAsync(checkServices)
  const checking = checks.state.status === 'loading'

  return (
    <div className={styles.page}>
      <title>Status · HomeFlix</title>
      <h1 className={styles.title}>Status</h1>
      <p className={styles.intro}>
        Whether each part of HomeFlix answers right now. The torrent client is not checked here: a download that
        fails shows its reason on the Downloads page.
      </p>

      {checking && <LoadingState label="Checking the services…" />}
      {checks.state.status === 'success' && (
        <ul className={styles.list} aria-label="Services">
          {checks.state.data.map((check) => (
            <li key={check.service} className={styles.item}>
              <div className={styles.itemHeader}>
                <h2 className={styles.itemTitle}>{SERVICE_LABEL[check.service]}</h2>
                <span className={`${styles.badge} ${styles[check.status]}`}>{STATUS_LABEL[check.status]}</span>
              </div>
              <p className={styles.hint}>{explain(check)}</p>
            </li>
          ))}
        </ul>
      )}

      <div className={styles.actions}>
        {/* Not disabled while checking: a disabled button would lose the focus. */}
        <Button variant="secondary" onClick={checks.reload}>
          Check again
        </Button>
      </div>
    </div>
  )
}

function explain(check: ServiceCheck): string {
  switch (check.status) {
    case 'running':
      return 'Working normally.'
    case 'unavailable':
      return `This part of HomeFlix does not answer. ${UNAVAILABLE_EFFECT[check.service]}`
    case 'unknown':
      return describeError(check.error)
  }
}
