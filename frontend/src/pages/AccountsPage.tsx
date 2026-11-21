import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { describeError } from '../api/errors'
import {
  changeAccountRole,
  createAccount,
  disableAccount,
  enableAccount,
  listAccounts,
  type ManagedAccount,
} from '../api/identity'
import { useAuth } from '../auth/AuthContext'
import { PASSWORD_MIN_LENGTH, validateSetup, type FieldErrors } from '../auth/validation'
import { Button } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { EmptyState, ErrorState, LoadingState } from '../components/StatusMessage'
import { TextField } from '../components/TextField'
import { formatDateTime } from '../format'
import { useAsync } from '../hooks/useAsync'
import styles from './AdminPage.module.css'

type Notice = { kind: 'success' | 'error'; text: string }
type Field = 'email' | 'password' | 'confirmation'

/**
 * Accounts and roles (G8; ACC-09, ACC-10): the household's accounts, creating one, making it an
 * administrator or a user, and disabling or enabling it. The server refuses to leave no enabled administrator.
 */
export function AccountsPage() {
  const { session } = useAuth()
  const ownId = session.status === 'signedIn' ? session.account.id : null
  const accounts = useAsync(listAccounts)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)

  async function apply(account: ManagedAccount, change: () => Promise<ManagedAccount>, done: string) {
    setBusyId(account.id)
    setNotice(null)
    try {
      await change()
      setNotice({ kind: 'success', text: done })
    } catch (error) {
      setNotice({ kind: 'error', text: describeError(error) })
    } finally {
      setBusyId(null)
      accounts.reload()
    }
  }

  return (
    <div className={styles.page}>
      <title>Accounts · HomeFlix</title>
      <h1 className={styles.title}>Accounts</h1>

      {notice && (
        <p className={notice.kind === 'error' ? styles.noticeError : styles.notice} role={notice.kind === 'error' ? 'alert' : 'status'}>
          {notice.text}
        </p>
      )}

      <section className={styles.section} aria-labelledby="accounts-heading">
        <h2 id="accounts-heading" className={styles.heading}>
          Household accounts
        </h2>
        <p className={styles.intro}>
          A change of role or a disabled account takes effect within 15 minutes, when that account's session is
          renewed.
        </p>
        {accounts.state.status === 'loading' && <LoadingState label="Loading the accounts…" />}
        {accounts.state.status === 'error' && (
          <ErrorState
            title="The accounts cannot be shown"
            message={describeError(accounts.state.error)}
            onRetry={accounts.reload}
          />
        )}
        {accounts.state.status === 'success' &&
          (accounts.state.data.length === 0 ? (
            <EmptyState title="No accounts yet" />
          ) : (
            <ul className={styles.list}>
              {accounts.state.data.map((account) => (
                <AccountItem
                  key={account.id}
                  account={account}
                  own={account.id === ownId}
                  busy={busyId !== null}
                  onRole={() =>
                    apply(
                      account,
                      () => changeAccountRole(account.id, !account.admin),
                      `${account.email} is now ${account.admin ? 'a user' : 'an administrator'}.`,
                    )
                  }
                  onAccess={() =>
                    account.disabled
                      ? apply(account, () => enableAccount(account.id), `${account.email} can sign in again.`)
                      : apply(account, () => disableAccount(account.id), `${account.email} can no longer sign in.`)
                  }
                />
              ))}
            </ul>
          ))}
      </section>

      <NewAccountForm
        onCreated={(account) => {
          setNotice({
            kind: 'success',
            text: `The account ${account.email} was created. Give its password to the person who will use it.`,
          })
          accounts.reload()
        }}
      />
    </div>
  )
}

function AccountItem({
  account,
  own,
  busy,
  onRole,
  onAccess,
}: {
  account: ManagedAccount
  own: boolean
  busy: boolean
  onRole: () => void
  onAccess: () => void
}) {
  const created = formatDateTime(account.createdAt)
  return (
    <li className={styles.item}>
      <div className={styles.itemHeader}>
        <h3 className={styles.itemTitle}>
          {account.email}
          {own && <span className={styles.meta}> (you)</span>}
        </h3>
        <span className={styles.tags}>
          <span className={`${styles.badge} ${account.admin ? styles.running : styles.unknown}`}>
            {account.admin ? 'Administrator' : 'User'}
          </span>
          {account.disabled && <span className={`${styles.badge} ${styles.unavailable}`}>Disabled</span>}
        </span>
      </div>
      {created && <p className={styles.dates}>Created {created}</p>}
      {own ? (
        <p className={styles.hint}>This is your account. Another administrator can change its role or disable it.</p>
      ) : (
        <div className={styles.actions}>
          <Button variant="secondary" onClick={onRole} disabled={busy} aria-label={roleLabel(account)}>
            {account.admin ? 'Make user' : 'Make administrator'}
          </Button>
          <Button
            variant="secondary"
            onClick={onAccess}
            disabled={busy}
            aria-label={`${account.disabled ? 'Enable' : 'Disable'} ${account.email}`}
          >
            {account.disabled ? 'Enable' : 'Disable'}
          </Button>
        </div>
      )}
    </li>
  )
}

function roleLabel(account: ManagedAccount): string {
  return account.admin ? `Make ${account.email} a user` : `Make ${account.email} an administrator`
}

const EMPTY = { email: '', password: '', confirmation: '' }

function NewAccountForm({ onCreated }: { onCreated: (account: ManagedAccount) => void }) {
  const [values, setValues] = useState(EMPTY)
  const [admin, setAdmin] = useState(false)
  const [errors, setErrors] = useState<FieldErrors<Field>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const emailRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)
  const confirmationRef = useRef<HTMLInputElement>(null)

  function update(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // The same rules as the first administrator's account (G8).
    const found = validateSetup(values)
    setErrors(found)
    setFormError(null)
    if (found.email) return emailRef.current?.focus()
    if (found.password) return passwordRef.current?.focus()
    if (found.confirmation) return confirmationRef.current?.focus()

    setSubmitting(true)
    try {
      const account = await createAccount({ email: values.email.trim(), password: values.password, admin })
      setValues(EMPTY)
      setAdmin(false)
      onCreated(account)
    } catch (error) {
      if (error instanceof ApiError && (error.fields.email || error.fields.password)) {
        // The server's own message for each rejected field (ADR-0021).
        setErrors({ email: error.fields.email, password: error.fields.password })
        if (error.fields.email) emailRef.current?.focus()
        else passwordRef.current?.focus()
      } else {
        setFormError(describeError(error))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className={styles.section} aria-labelledby="new-account-heading">
      <h2 id="new-account-heading" className={styles.heading}>
        Create an account
      </h2>
      <p className={styles.intro}>
        Choose the password yourself, then give it to the person who will use the account.
      </p>
      {formError && (
        <div className={formStyles.alert} role="alert">
          <p>{formError}</p>
        </div>
      )}
      <form className={`${formStyles.form} ${styles.form}`} onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <TextField
          ref={emailRef}
          label="Email address"
          name="email"
          type="email"
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          value={values.email}
          onChange={(event) => update('email', event.target.value)}
          error={errors.email}
          required
        />
        <TextField
          ref={passwordRef}
          label="Password"
          hint={`At least ${PASSWORD_MIN_LENGTH} characters.`}
          name="password"
          type="password"
          autoComplete="new-password"
          value={values.password}
          onChange={(event) => update('password', event.target.value)}
          error={errors.password}
          required
        />
        <TextField
          ref={confirmationRef}
          label="Confirm password"
          name="confirmation"
          type="password"
          autoComplete="new-password"
          value={values.confirmation}
          onChange={(event) => update('confirmation', event.target.value)}
          error={errors.confirmation}
          required
        />
        <label className={styles.checkbox}>
          <input type="checkbox" checked={admin} onChange={(event) => setAdmin(event.target.checked)} />
          <span>
            Administrator
            <span className={styles.hint}>Can manage accounts, the library and downloads.</span>
          </span>
        </label>
        <div className={styles.actions}>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Creating the account…' : 'Create the account'}
          </Button>
        </div>
      </form>
    </section>
  )
}
