import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { describeError } from '../api/errors'
import { createProfile, listProfiles, type Profile } from '../api/identity'
import { useAuth } from '../auth/AuthContext'
import { redirectTarget } from '../auth/redirect'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { ErrorState, LoadingState } from '../components/StatusMessage'
import { TextField } from '../components/TextField'
import { useAsync } from '../hooks/useAsync'
import styles from './ProfilesPage.module.css'

/** The limits of docs/api/identity.yaml (PRF-01). */
const MAX_PROFILES = 5
const MAX_NAME_LENGTH = 30

type Notice = { kind: 'success' | 'error'; text: string }

/**
 * Profile selection (G10; PRF-02, PRF-03, UX-04, UX-05): shown after sign-in and whenever the session has
 * no active profile, and reachable from the header to switch. Choosing a profile makes the server re-issue
 * the session with it, so the list and the progress shown afterwards are that profile's.
 */
export function ProfilesPage() {
  const { session, selectProfile, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const profiles = useAsync(listProfiles)
  const [choosing, setChoosing] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const noticeRef = useRef<HTMLParagraphElement>(null)

  const activeId = session.status === 'signedIn' ? session.account.profileId : null

  // Creating or choosing disables its button and may reload the list: focus goes to the outcome.
  useEffect(() => {
    if (notice) noticeRef.current?.focus()
  }, [notice])

  async function choose(profile: Profile) {
    setChoosing(profile.id)
    setNotice(null)
    try {
      await selectProfile(profile.id)
      navigate(redirectTarget(location.state), { replace: true })
    } catch (error) {
      setNotice({ kind: 'error', text: describeError(error) })
      setChoosing(null)
    }
  }

  return (
    <AuthLayout
      title="Who is watching?"
      intro="Each profile has its own list and remembers where you stopped in each title."
    >
      {notice && (
        <p
          ref={noticeRef}
          tabIndex={-1}
          className={notice.kind === 'error' ? formStyles.alert : formStyles.notice}
          role={notice.kind === 'error' ? 'alert' : 'status'}
        >
          {notice.text}
        </p>
      )}

      {profiles.state.status === 'loading' && <LoadingState label="Loading the profiles…" />}
      {profiles.state.status === 'error' && (
        <ErrorState
          title="The profiles cannot be shown"
          message={describeError(profiles.state.error)}
          onRetry={profiles.reload}
        />
      )}
      {profiles.state.status === 'success' && (
        <>
          {profiles.state.data.length === 0 ? (
            <p className={styles.empty}>This account has no profile yet. Create one to start watching.</p>
          ) : (
            <ul className={styles.list} aria-label="Profiles">
              {profiles.state.data.map((profile) => (
                <li key={profile.id}>
                  <button
                    type="button"
                    className={styles.profile}
                    onClick={() => choose(profile)}
                    disabled={choosing !== null}
                    aria-current={profile.id === activeId ? 'true' : undefined}
                  >
                    <span className={styles.initial} aria-hidden="true">
                      {profile.name.charAt(0).toUpperCase()}
                    </span>
                    <span className={styles.name}>{profile.name}</span>
                    {profile.id === activeId && <span className={styles.current}>Current</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {profiles.state.data.length < MAX_PROFILES ? (
            <NewProfileForm
              first={profiles.state.data.length === 0}
              onCreated={(profile) => {
                setNotice({ kind: 'success', text: `The profile ${profile.name} was created. Choose it to start watching.` })
                profiles.reload()
              }}
            />
          ) : (
            <p className={styles.hint}>This account has the maximum of {MAX_PROFILES} profiles.</p>
          )}
        </>
      )}

      <Button variant="secondary" className={styles.signOut} onClick={() => signOut().catch(() => undefined)}>
        Sign out
      </Button>
    </AuthLayout>
  )
}

function NewProfileForm({ first, onCreated }: { first: boolean; onCreated: (profile: Profile) => void }) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | undefined>()
  const [submitting, setSubmitting] = useState(false)
  const ref = useRef<HTMLInputElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setError('Enter a name for the profile.')
      return ref.current?.focus()
    }
    setError(undefined)
    setSubmitting(true)
    try {
      const profile = await createProfile(trimmed)
      setName('')
      onCreated(profile)
    } catch (failure) {
      // A refused name or the limit: the server's message, on the field (ADR-0021).
      setError((failure instanceof ApiError && failure.fields.name) || describeError(failure))
      ref.current?.focus()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={formStyles.form} onSubmit={handleSubmit} noValidate aria-busy={submitting}>
      <TextField
        ref={ref}
        label={first ? 'Name of your first profile' : 'New profile'}
        hint={`Up to ${MAX_NAME_LENGTH} characters, different from the other profiles of this account.`}
        name="profileName"
        autoComplete="off"
        maxLength={MAX_NAME_LENGTH}
        value={name}
        onChange={(event) => setName(event.target.value)}
        error={error}
        required
      />
      <Button type="submit" variant={first ? 'primary' : 'secondary'} className={formStyles.submit} disabled={submitting}>
        {submitting ? 'Creating…' : 'Create the profile'}
      </Button>
    </form>
  )
}
