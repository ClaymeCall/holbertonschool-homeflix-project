import { useEffect, useRef, useState } from 'react'
import {
  cancelDownload,
  isActive,
  listDownloads,
  listEntries,
  startDownload,
  type Download,
  type Entry,
} from '../api/acquisition'
import { describeError } from '../api/errors'
import { Button } from '../components/Button'
import { Dialog } from '../components/Dialog'
import { EmptyState, ErrorState, LoadingState } from '../components/StatusMessage'
import { formatBytes, formatDateTime } from '../format'
import { useAsync } from '../hooks/useAsync'
import { useLiveData } from '../hooks/useLiveData'
import { downloadStateLabel } from '../labels'
import styles from './AdminPage.module.css'

/** While a download is queued or running, the list is read again this often (the server checks every 10 s). */
export const REFRESH_MS = 5000

/** The downloads shown on one page of the history. */
export const PAGE_SIZE = 10

function pollWhileActive(downloads: Download[]): number | null {
  return downloads.some(isActive) ? REFRESH_MS : null
}

/** Queued or downloading at 100 %: the file is being checked and added to the library. */
function isFinishing(download: Download): boolean {
  return download.state === 'DOWNLOADING' && download.progressPercent >= 100
}

type Notice = { kind: 'success' | 'error'; text: string }

/**
 * The administrator's acquisition screen (J9; ACQ-01, ACQ-03, ACQ-04, ACQ-11): the downloads with their
 * state, and the verified catalogue they are started from. A download only names a catalogue entry: there is
 * no field for a URL or a magnet link (ACQ-09, ACQ-10). The rights reminder is shown before every start.
 */
export function DownloadsPage() {
  const catalogue = useAsync(listEntries)
  const downloads = useLiveData(listDownloads, pollWhileActive)
  const [reminderFor, setReminderFor] = useState<Entry | null>(null)
  const [cancelling, setCancelling] = useState<Download | null>(null)
  const [page, setPage] = useState(0)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)
  const noticeRef = useRef<HTMLParagraphElement>(null)

  // A start or a cancel disables or removes the button that opened its dialog, so focus goes to the outcome.
  useEffect(() => {
    if (notice) noticeRef.current?.focus()
  }, [notice])

  const entries = catalogue.state.status === 'success' ? catalogue.state.data : []
  const list = downloads.state.status === 'success' ? downloads.state.data : []
  const titleOf = (entryId: string) => entries.find((entry) => entry.id === entryId)?.title ?? 'Catalogue entry'
  // The list is read whole, and shown PAGE_SIZE at a time, newest first. A page that no longer exists is left behind.
  const pageCount = Math.max(1, Math.ceil(list.length / PAGE_SIZE))
  const current = Math.min(page, pageCount - 1)
  const visible = list.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE)
  const activeEntries = new Set(list.filter(isActive).map((download) => download.entryId))

  async function confirmStart() {
    if (!reminderFor) return
    const entry = reminderFor
    setBusy(true)
    try {
      await startDownload(entry.id)
      setNotice({ kind: 'success', text: `The download of ${entry.title} has started.` })
    } catch (error) {
      setNotice({ kind: 'error', text: describeError(error) })
    } finally {
      setBusy(false)
      setReminderFor(null)
      downloads.refresh()
    }
  }

  async function confirmCancel() {
    if (!cancelling) return
    const title = titleOf(cancelling.entryId)
    setBusy(true)
    try {
      await cancelDownload(cancelling.id)
      setNotice({ kind: 'success', text: `The download of ${title} was cancelled.` })
    } catch (error) {
      setNotice({ kind: 'error', text: describeError(error) })
    } finally {
      setBusy(false)
      setCancelling(null)
      downloads.refresh()
    }
  }

  return (
    <div className={styles.page}>
      <title>Downloads · HomeFlix</title>
      <h1 className={styles.title}>Downloads</h1>

      {notice && (
        <p
          ref={noticeRef}
          tabIndex={-1}
          className={notice.kind === 'error' ? styles.noticeError : styles.notice}
          role={notice.kind === 'error' ? 'alert' : 'status'}
        >
          {notice.text}
        </p>
      )}

      <section className={styles.section} aria-labelledby="catalogue-heading">
        <h2 id="catalogue-heading" className={styles.heading}>
          Verified catalogue
        </h2>
        <p className={styles.intro}>
          Only content the team has checked can be downloaded. Each entry shows where it comes from and the licence
          that allows it to be copied and shared.
        </p>
        {catalogue.state.status === 'loading' && <LoadingState label="Loading the catalogue…" />}
        {catalogue.state.status === 'error' && (
          <ErrorState
            title="The catalogue cannot be shown"
            message={describeError(catalogue.state.error)}
            onRetry={catalogue.reload}
          />
        )}
        {catalogue.state.status === 'success' &&
          (entries.length === 0 ? (
            <EmptyState title="The verified catalogue is empty">
              <p>Entries are added by the team after checking their licence.</p>
            </EmptyState>
          ) : (
            <ul className={styles.list}>
              {entries.map((entry) => (
                <EntryItem
                  key={entry.id}
                  entry={entry}
                  downloading={activeEntries.has(entry.id)}
                  onDownload={() => {
                    setNotice(null)
                    setReminderFor(entry)
                  }}
                />
              ))}
            </ul>
          ))}
      </section>

      <section className={styles.section} aria-labelledby="downloads-heading">
        <h2 id="downloads-heading" className={styles.heading}>
          All downloads
        </h2>
        {downloads.state.status === 'loading' && <LoadingState label="Loading the downloads…" />}
        {downloads.state.status === 'error' && (
          <ErrorState
            title="The downloads cannot be shown"
            message={describeError(downloads.state.error)}
            onRetry={downloads.retry}
          />
        )}
        {downloads.state.status === 'success' &&
          (list.length === 0 ? (
            <EmptyState title="No downloads yet">
              <p>Start one from the verified catalogue above.</p>
            </EmptyState>
          ) : (
            <>
              {downloads.stale && (
                <p className={styles.stale} role="status">
                  The list could not be refreshed. It is shown as it was a moment ago.
                </p>
              )}
              <ul className={styles.list}>
                {visible.map((download) => (
                  <DownloadItem
                    key={download.id}
                    download={download}
                    title={titleOf(download.entryId)}
                    onCancel={() => setCancelling(download)}
                  />
                ))}
              </ul>
              {pageCount > 1 && (
                <nav className={styles.pager} aria-label="Download pages">
                  <Button variant="secondary" onClick={() => setPage(current - 1)} disabled={current === 0}>
                    Previous
                  </Button>
                  <span className={styles.pageInfo}>
                    Page {current + 1} of {pageCount}
                  </span>
                  <Button variant="secondary" onClick={() => setPage(current + 1)} disabled={current >= pageCount - 1}>
                    Next
                  </Button>
                </nav>
              )}
            </>
          ))}
      </section>

      {reminderFor && (
        <Dialog
          title="Before you download"
          onClose={() => setReminderFor(null)}
          actions={
            <>
              <Button variant="secondary" onClick={() => setReminderFor(null)} disabled={busy}>
                Cancel
              </Button>
              <Button onClick={confirmStart} disabled={busy}>
                {busy ? 'Starting…' : 'I understand'}
              </Button>
            </>
          }
        >
          {/* Reference text: docs/copyright-and-p2p.md, "In-app reminder" (J2). Change it there first. */}
          <p>
            HomeFlix uses peer-to-peer to transfer files. While a file downloads, and for a limited time afterwards,
            pieces of it are automatically shared with other people.
          </p>
          <p>
            Peer-to-peer gives you no rights over a work. Only download content whose licence allows both copying{' '}
            <strong>and</strong> sharing.
          </p>
          <p>This reminder does not replace checking the rights.</p>
          <p className={styles.dialogEntry}>
            <span className="visually-hidden">Title to download: </span>
            {reminderFor.title} · {reminderFor.licence}
          </p>
        </Dialog>
      )}

      {cancelling && (
        <Dialog
          title="Cancel this download?"
          onClose={() => setCancelling(null)}
          actions={
            <>
              <Button variant="secondary" onClick={() => setCancelling(null)} disabled={busy}>
                Keep downloading
              </Button>
              <Button onClick={confirmCancel} disabled={busy}>
                {busy ? 'Cancelling…' : 'Cancel download'}
              </Button>
            </>
          }
        >
          <p>
            The download of {titleOf(cancelling.entryId)} stops and the part already downloaded is deleted. You can
            start it again later.
          </p>
        </Dialog>
      )}
    </div>
  )
}

function DownloadItem({ download, title, onCancel }: { download: Download; title: string; onCancel: () => void }) {
  const finishing = isFinishing(download)
  const label = finishing ? 'Finishing' : downloadStateLabel[download.state]
  const started = formatDateTime(download.createdAt)
  const finished = formatDateTime(download.finishedAt)
  return (
    <li className={styles.item}>
      <div className={styles.itemHeader}>
        <h3 className={styles.itemTitle}>{title}</h3>
        <span className={`${styles.badge} ${styles[download.state]}`}>{label}</span>
      </div>
      {isActive(download) && (
        <div className={styles.progress}>
          <progress value={download.progressPercent} max={100} aria-label={`${title}, progress`} />
          <span>{download.progressPercent} %</span>
        </div>
      )}
      {finishing && <p className={styles.hint}>The file is being checked and added to the library.</p>}
      {download.state === 'FAILED' && download.errorMessage && <p className={styles.reason}>{download.errorMessage}</p>}
      <p className={styles.dates}>
        {started && <span>Started {started}</span>}
        {finished && <span>{download.state === 'COMPLETED' ? 'Completed' : 'Ended'} {finished}</span>}
      </p>
      {isActive(download) && !finishing && (
        <div className={styles.actions}>
          <Button variant="secondary" onClick={onCancel} aria-label={`Cancel the download of ${title}`}>
            Cancel
          </Button>
        </div>
      )}
    </li>
  )
}

function EntryItem({ entry, downloading, onDownload }: { entry: Entry; downloading: boolean; onDownload: () => void }) {
  return (
    <li className={styles.item}>
      <div className={styles.itemHeader}>
        <h3 className={styles.itemTitle}>{entry.title}</h3>
        <span className={styles.meta}>
          {entry.expectedFormat} · {formatBytes(entry.expectedSizeBytes)}
        </span>
      </div>
      <dl className={styles.facts}>
        <div>
          <dt>Source</dt>
          <dd>
            <a href={entry.sourceUrl} target="_blank" rel="noopener noreferrer">
              {entry.sourceName}
              <span className="visually-hidden"> (opens in a new tab)</span>
            </a>
          </dd>
        </div>
        <div>
          <dt>Licence</dt>
          <dd>
            <a href={entry.licenceUrl} target="_blank" rel="noopener noreferrer">
              {entry.licence}
              <span className="visually-hidden"> (opens in a new tab)</span>
            </a>
          </dd>
        </div>
        {entry.attribution && (
          <div>
            <dt>Attribution</dt>
            <dd>{entry.attribution}</dd>
          </div>
        )}
      </dl>
      <div className={styles.actions}>
        <Button
          onClick={onDownload}
          disabled={downloading}
          aria-label={`${downloading ? 'Downloading' : 'Download'} ${entry.title}`}
        >
          {downloading ? 'Downloading…' : 'Download'}
        </Button>
      </div>
      {downloading && <p className={styles.hint}>This title is already downloading. Follow it in All downloads.</p>}
    </li>
  )
}
