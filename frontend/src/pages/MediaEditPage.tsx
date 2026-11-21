import { useCallback, useId, useRef, useState, type FormEvent, type Ref } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { describeError, isNotFound } from '../api/errors'
import { getMedia, updateMedia, type MediaDetails, type MediaUpdate } from '../api/library'
import { Button, ButtonLink } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { ErrorState, LoadingState } from '../components/StatusMessage'
import { TextField } from '../components/TextField'
import fieldStyles from '../components/TextField.module.css'
import { useAsync } from '../hooks/useAsync'
import { availabilityLabel } from '../labels'
import styles from './AdminPage.module.css'

type Field = 'title' | 'description' | 'genres' | 'duration' | 'posterUrl'
type Values = Record<Field, string>
type Errors = Partial<Record<Field, string>>

/** The contract's field names, mapped to the fields of this form. */
const SERVER_FIELD: Record<string, Field> = {
  title: 'title',
  description: 'description',
  genres: 'genres',
  durationSeconds: 'duration',
  posterUrl: 'posterUrl',
}

function minutes(seconds: number | null): string {
  return seconds === null ? '' : String(Math.round(seconds / 60))
}

function valuesOf(media: MediaDetails): Values {
  return {
    title: media.title,
    description: media.description ?? '',
    genres: media.genres.join(', '),
    duration: minutes(media.durationSeconds),
    posterUrl: media.posterUrl ?? '',
  }
}

/**
 * Metadata editing of one title (H8; CAT-09, CAT-10): complete or correct what the scan took from the file
 * name. The server checks every field again and keeps the edit when the library is scanned again.
 */
export function MediaEditPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => getMedia(id, signal), [id])
  const media = useAsync(load)

  if (media.state.status === 'loading') return <LoadingState label="Loading the title…" />
  if (media.state.status === 'error') {
    return isNotFound(media.state.error) ? (
      <ErrorState title="Title not found" message="This title is no longer in the catalogue.">
        <ButtonLink to="/admin/library" variant="secondary">
          Back to the library
        </ButtonLink>
      </ErrorState>
    ) : (
      <ErrorState
        title="The title cannot be shown"
        message={describeError(media.state.error)}
        onRetry={media.reload}
      />
    )
  }
  return <MediaForm key={media.state.data.id} media={media.state.data} />
}

function MediaForm({ media }: { media: MediaDetails }) {
  const [saved, setSaved] = useState(media)
  const [values, setValues] = useState(() => valuesOf(media))
  const [errors, setErrors] = useState<Errors>({})
  const [notice, setNotice] = useState<{ kind: 'success' | 'error'; text: string } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const titleRef = useRef<HTMLInputElement>(null)
  const descriptionRef = useRef<HTMLTextAreaElement>(null)
  const genresRef = useRef<HTMLInputElement>(null)
  const durationRef = useRef<HTMLInputElement>(null)
  const posterRef = useRef<HTMLInputElement>(null)

  function update(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
  }

  function showErrors(found: Errors) {
    setErrors(found)
    // The first field in error, in the order of the form.
    if (found.title) titleRef.current?.focus()
    else if (found.description) descriptionRef.current?.focus()
    else if (found.genres) genresRef.current?.focus()
    else if (found.duration) durationRef.current?.focus()
    else if (found.posterUrl) posterRef.current?.focus()
  }

  /** The form in the shape of the contract. The duration is typed in minutes and sent in seconds. */
  function toUpdate(): { errors: Errors } | { update: MediaUpdate } {
    const title = values.title.trim()
    const duration = values.duration.trim()
    const found: Errors = {}
    if (!title) found.title = 'Enter a title.'
    if (duration && !/^\d+$/.test(duration)) found.duration = 'Enter the duration in whole minutes, for example 95.'
    if (Object.keys(found).length > 0) return { errors: found }
    const update: MediaUpdate = {
      title,
      description: values.description.trim() || null,
      genres: values.genres.split(',').map((genre) => genre.trim()).filter(Boolean),
      // An unchanged duration keeps its seconds instead of being rounded to the minute.
      durationSeconds: !duration ? null : duration === minutes(saved.durationSeconds) ? saved.durationSeconds : Number(duration) * 60,
      posterUrl: values.posterUrl.trim() || null,
    }
    return { update }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setNotice(null)
    const result = toUpdate()
    if ('errors' in result) return showErrors(result.errors)
    setErrors({})
    setSubmitting(true)
    try {
      const next = await updateMedia(media.id, result.update)
      setSaved(next)
      setValues(valuesOf(next))
      setNotice({ kind: 'success', text: 'The changes were saved.' })
    } catch (error) {
      const rejected: Errors = {}
      if (error instanceof ApiError) {
        for (const [name, message] of Object.entries(error.fields)) {
          const field = SERVER_FIELD[name]
          if (field) rejected[field] = message
        }
      }
      if (Object.keys(rejected).length > 0) showErrors(rejected)
      else setNotice({ kind: 'error', text: describeError(error) })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className={styles.page}>
      <title>{`Edit ${saved.title} · HomeFlix`}</title>
      <div>
        <ButtonLink to="/admin/library" variant="secondary">
          Back to the library
        </ButtonLink>
      </div>
      <h1 className={styles.title}>{saved.title}</h1>
      <p className={styles.meta}>{availabilityLabel[saved.availability]}</p>

      {notice && (
        <p className={notice.kind === 'error' ? styles.noticeError : styles.notice} role={notice.kind === 'error' ? 'alert' : 'status'}>
          {notice.text}
        </p>
      )}

      <form className={`${formStyles.form} ${styles.form}`} onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <TextField
          ref={titleRef}
          label="Title"
          name="title"
          value={values.title}
          onChange={(event) => update('title', event.target.value)}
          error={errors.title}
          maxLength={500}
          required
        />
        <TextArea
          ref={descriptionRef}
          label="Description"
          value={values.description}
          onChange={(value) => update('description', value)}
          error={errors.description}
        />
        <TextField
          ref={genresRef}
          label="Genres"
          hint="Separated by commas, for example Animation, Fantasy."
          name="genres"
          value={values.genres}
          onChange={(event) => update('genres', event.target.value)}
          error={errors.genres}
        />
        <TextField
          ref={durationRef}
          label="Duration in minutes"
          name="duration"
          inputMode="numeric"
          value={values.duration}
          onChange={(event) => update('duration', event.target.value)}
          error={errors.duration}
        />
        <TextField
          ref={posterRef}
          label="Poster address"
          hint="An https address of an image. Leave it empty to show the default poster."
          name="posterUrl"
          type="url"
          autoCapitalize="none"
          spellCheck={false}
          value={values.posterUrl}
          onChange={(event) => update('posterUrl', event.target.value)}
          error={errors.posterUrl}
        />
        <div className={styles.actions}>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save the changes'}
          </Button>
        </div>
      </form>
    </div>
  )
}

function TextArea({
  ref,
  label,
  value,
  onChange,
  error,
}: {
  ref: Ref<HTMLTextAreaElement>
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
}) {
  const id = useId()
  const errorId = error ? `${id}-error` : undefined
  return (
    <div className={fieldStyles.field}>
      <label className={fieldStyles.label} htmlFor={id}>
        {label}
      </label>
      <textarea
        ref={ref}
        id={id}
        className={`${fieldStyles.input} ${styles.textarea}`}
        rows={5}
        maxLength={5000}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
      />
      {error && (
        <p id={errorId} className={fieldStyles.error}>
          {error}
        </p>
      )}
    </div>
  )
}
