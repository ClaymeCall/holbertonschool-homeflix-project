import { useRef, useState, type FormEvent } from 'react'
import { describeError } from '../api/errors'
import { useAuth, type SignedOutReason } from '../auth/AuthContext'
import { validateSignIn, type FieldErrors } from '../auth/validation'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { TextField } from '../components/TextField'

const reasonMessage: Record<SignedOutReason, string> = {
  expired: 'Your session has expired. Sign in again to continue.',
  signedOut: 'You are signed out. See you soon!',
  setupDone: 'The administrator account has been created. Sign in to get started.',
}

export function SignInPage() {
  const { session, signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors<'email' | 'password'>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const emailRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)

  const reason = session.status === 'signedOut' ? session.reason : undefined

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateSignIn({ email, password })
    setErrors(found)
    setFormError(null)
    if (found.email) return emailRef.current?.focus()
    if (found.password) return passwordRef.current?.focus()

    setSubmitting(true)
    try {
      await signIn({ email: email.trim(), password })
      // The guard redirects to the requested page once the session is signed in.
    } catch (error) {
      setFormError(describeError(error))
      setPassword('')
      passwordRef.current?.focus()
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Sign in">
      {reason && !formError && (
        <p className={formStyles.notice} role="status">
          {reasonMessage[reason]}
        </p>
      )}
      {formError && (
        <p className={formStyles.alert} role="alert">
          {formError}
        </p>
      )}
      <form className={formStyles.form} onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <TextField
          ref={emailRef}
          label="Email address"
          name="email"
          type="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={errors.email}
          required
        />
        <TextField
          ref={passwordRef}
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={errors.password}
          required
        />
        <Button type="submit" className={formStyles.submit} disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthLayout>
  )
}
