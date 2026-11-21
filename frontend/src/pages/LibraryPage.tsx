import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { describeError } from '../api/errors'
import {
  addRoot,
  listAllMedia,
  listRoots,
  removeRoot,
  runScan,
  type InaccessibleRootError,
  type Root,
  type ScanReport,
} from '../api/library'
import { Button, ButtonLink } from '../components/Button'
import { Dialog } from '../components/Dialog'
import formStyles from '../components/Form.module.css'
import { EmptyState, ErrorState, LoadingState } from '../components/StatusMessage'
import { TextField } from '../components/TextField'
import { formatDateTime } from '../format'
import { useAsync } from '../hooks/useAsync'
import { availabilityLabel } from '../labels'
import styles from './AdminPage.module.css'

type Notice = { kind: 'success' | 'error'; text: string }

/** The scan report gives codes without a message, so the interface words them (ADR-0021). */
const INACCESSIBLE_REASON: Record<InaccessibleRootError, string> = {
  directory_not_found: 'The folder no longer exists.',
  directory_not_readable: 'HomeFlix is not allowed to read this folder.',
  not_a_directory: 'This path is no longer a folder.',
}

/**
 * The library (H2, H5, H8; LIB-01, LIB-06, CAT-09): the authorized media folders, indexing them on
 * request with its report, and the titles of the catalogue, each with a link to edit its information.
 */
export function LibraryPage() {
  const roots = useAsync(listRoots)
  const media = useAsync(listAllMedia)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [removing, setRemoving] = useState<Root | null>(null)
  const [busy, setBusy] = useState(false)
  const [scanning, setScanning] = useState(false)
  const [report, setReport] = useState<ScanReport | null>(null)

  const rootList = roots.state.status === 'success' ? roots.state.data : []
  const pathOf = (rootId: string) => rootList.find((root) => root.id === rootId)?.path ?? 'A removed folder'

  async function confirmRemove() {
    if (!removing) return
    const root = removing
    setBusy(true)
    try {
      await removeRoot(root.id)
      setNotice({ kind: 'success', text: `${root.path} was removed. Its titles are now unavailable.` })
    } catch (error) {
      setNotice({ kind: 'error', text: describeError(error) })
    } finally {
      setBusy(false)
      setRemoving(null)
      roots.reload()
      media.reload()
    }
  }

  async function scan() {
    setScanning(true)
    setNotice(null)
    setReport(null)
    try {
      setReport(await runScan())
    } catch (error) {
      setNotice({ kind: 'error', text: describeError(error) })
    } finally {
      setScanning(false)
      media.reload()
    }
  }

  return (
    <div className={styles.page}>
      <title>Library · HomeFlix</title>
      <h1 className={styles.title}>Library</h1>

      {notice && (
        <p className={notice.kind === 'error' ? styles.noticeError : styles.notice} role={notice.kind === 'error' ? 'alert' : 'status'}>
          {notice.text}
        </p>
      )}

      <section className={styles.section} aria-labelledby="roots-heading">
        <h2 id="roots-heading" className={styles.heading}>
          Media folders
        </h2>
        <p className={styles.intro}>
          HomeFlix only reads the folders listed here. Removing one never deletes a file.
        </p>
        {roots.state.status === 'loading' && <LoadingState label="Loading the folders…" />}
        {roots.state.status === 'error' && (
          <ErrorState title="The folders cannot be shown" message={describeError(roots.state.error)} onRetry={roots.reload} />
        )}
        {roots.state.status === 'success' &&
          (rootList.length === 0 ? (
            <EmptyState title="No media folder yet">
              <p>Add the folder that holds your films, then scan it.</p>
            </EmptyState>
          ) : (
            <ul className={styles.list} aria-label="Media folders">
              {rootList.map((root) => (
                <li key={root.id} className={styles.item}>
                  <div className={styles.itemHeader}>
                    <h3 className={styles.itemTitle}>{root.path}</h3>
                    <Button
                      variant="secondary"
                      onClick={() => setRemoving(root)}
                      aria-label={`Remove ${root.path}`}
                    >
                      Remove
                    </Button>
                  </div>
                  {formatDateTime(root.createdAt) && <p className={styles.dates}>Added {formatDateTime(root.createdAt)}</p>}
                </li>
              ))}
            </ul>
          ))}
        <AddRootForm
          onAdded={(root) => {
            setNotice({ kind: 'success', text: `${root.path} was added. Scan the folders to add its films.` })
            roots.reload()
          }}
        />
      </section>

      <section className={styles.section} aria-labelledby="scan-heading">
        <h2 id="scan-heading" className={styles.heading}>
          Indexing
        </h2>
        <p className={styles.intro}>
          A scan adds the new films of the folders and marks the missing ones as unavailable. Scanning again never
          adds a film twice. It can take a while on a large library.
        </p>
        <div className={styles.actions}>
          <Button onClick={scan} disabled={scanning || rootList.length === 0}>
            {scanning ? 'Scanning…' : 'Scan the folders'}
          </Button>
        </div>
        {scanning && <LoadingState label="Scanning the folders…" />}
        {report && <ScanResult report={report} pathOf={pathOf} />}
      </section>

      <section className={styles.section} aria-labelledby="titles-heading">
        <h2 id="titles-heading" className={styles.heading}>
          Titles
        </h2>
        <p className={styles.intro}>Complete or correct the information of a title. A scan never overwrites it.</p>
        {media.state.status === 'loading' && <LoadingState label="Loading the titles…" />}
        {media.state.status === 'error' && (
          <ErrorState title="The titles cannot be shown" message={describeError(media.state.error)} onRetry={media.reload} />
        )}
        {media.state.status === 'success' &&
          (media.state.data.length === 0 ? (
            <EmptyState title="No titles yet">
              <p>Scan the folders to add the films they contain.</p>
            </EmptyState>
          ) : (
            <ul className={styles.list} aria-label="Titles">
              {media.state.data.map((title) => (
                <li key={title.id} className={styles.row}>
                  <span className={styles.itemTitle}>{title.title}</span>
                  <span className={styles.meta}>{availabilityLabel[title.availability]}</span>
                  <ButtonLink
                    to={`/admin/library/media/${encodeURIComponent(title.id)}`}
                    variant="secondary"
                    aria-label={`Edit ${title.title}`}
                  >
                    Edit
                  </ButtonLink>
                </li>
              ))}
            </ul>
          ))}
      </section>

      {removing && (
        <Dialog
          title="Remove this folder?"
          onClose={() => setRemoving(null)}
          actions={
            <>
              <Button variant="secondary" onClick={() => setRemoving(null)} disabled={busy}>
                Keep the folder
              </Button>
              <Button onClick={confirmRemove} disabled={busy}>
                {busy ? 'Removing…' : 'Remove the folder'}
              </Button>
            </>
          }
        >
          <p>HomeFlix stops reading {removing.path}. No file is deleted.</p>
          <p>Its titles become unavailable and keep their information, favourites and progress.</p>
        </Dialog>
      )}
    </div>
  )
}

function ScanResult({ report, pathOf }: { report: ScanReport; pathOf: (rootId: string) => string }) {
  return (
    <div className={styles.item}>
      <p role="status">
        Scan finished: {count(report.added, 'title')} added, {count(report.updated, 'title')} seen again,{' '}
        {count(report.markedUnavailable, 'title')} marked unavailable.
      </p>
      {report.inaccessibleRoots.length > 0 && (
        <>
          <h3 className={styles.itemTitle}>Folders that could not be read</h3>
          <ul className={styles.plainList}>
            {report.inaccessibleRoots.map((root) => (
              <li key={root.rootId}>
                {pathOf(root.rootId)}: {INACCESSIBLE_REASON[root.error]}
              </li>
            ))}
          </ul>
        </>
      )}
      {report.unsupported.length > 0 && (
        <>
          <h3 className={styles.itemTitle}>Videos not added</h3>
          <p className={styles.hint}>HomeFlix only plays MP4 files with H.264 video and AAC sound.</p>
          <ul className={styles.plainList}>
            {report.unsupported.map((file) => (
              <li key={`${file.rootId}/${file.relativePath}`}>
                {pathOf(file.rootId)}/{file.relativePath}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function count(value: number, noun: string): string {
  return `${value} ${noun}${value === 1 ? '' : 's'}`
}

function AddRootForm({ onAdded }: { onAdded: (root: Root) => void }) {
  const [path, setPath] = useState('')
  const [error, setError] = useState<string | undefined>()
  const [submitting, setSubmitting] = useState(false)
  const ref = useRef<HTMLInputElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!path.trim()) {
      setError('Enter the path of a folder.')
      return ref.current?.focus()
    }
    setError(undefined)
    setSubmitting(true)
    try {
      const root = await addRoot(path.trim())
      setPath('')
      onAdded(root)
    } catch (failure) {
      // Every refusal has its own message for the user (ADR-0021), shown on the field.
      setError((failure instanceof ApiError && failure.fields.path) || describeError(failure))
      ref.current?.focus()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={`${formStyles.form} ${styles.form}`} onSubmit={handleSubmit} noValidate aria-busy={submitting}>
      <TextField
        ref={ref}
        label="Folder to add"
        hint="A folder inside /media/library or /media/downloads, for example /media/library/films."
        name="path"
        autoComplete="off"
        autoCapitalize="none"
        spellCheck={false}
        value={path}
        onChange={(event) => setPath(event.target.value)}
        error={error}
        required
      />
      <div className={styles.actions}>
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Adding…' : 'Add the folder'}
        </Button>
      </div>
    </form>
  )
}
