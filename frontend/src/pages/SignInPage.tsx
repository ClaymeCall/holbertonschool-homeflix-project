import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { describeError } from '../api/errors'
import { useAuth, type SignedOutReason } from '../auth/AuthContext'
import { validateSignIn, type FieldErrors } from '../auth/validation'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { TextField } from '../components/TextField'

const reasonMessage: Record<SignedOutReason, string> = {
  expired: 'Votre session a expiré. Reconnectez-vous pour continuer.',
  signedOut: 'Vous êtes déconnecté. À bientôt !',
  setupDone: 'Le compte administrateur est créé. Connectez-vous pour commencer.',
}

function signInErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Nom d'utilisateur ou mot de passe incorrect."
    if (error.status === 403) return "Ce compte est désactivé. Contactez l'administrateur de la maison."
    if (error.status === 429) return 'Trop de tentatives. Patientez un instant puis réessayez.'
  }
  return describeError(error)
}

export function SignInPage() {
  const { session, signIn } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors<'username' | 'password'>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const usernameRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)

  const reason = session.status === 'signedOut' ? session.reason : undefined

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateSignIn({ username, password })
    setErrors(found)
    setFormError(null)
    if (found.username) return usernameRef.current?.focus()
    if (found.password) return passwordRef.current?.focus()

    setSubmitting(true)
    try {
      await signIn({ username: username.trim(), password })
      // The guard redirects to the requested page once the session is signed in.
    } catch (error) {
      setFormError(signInErrorMessage(error))
      setPassword('')
      passwordRef.current?.focus()
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Connexion">
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
          ref={usernameRef}
          label="Nom d'utilisateur"
          name="username"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          error={errors.username}
          required
        />
        <TextField
          ref={passwordRef}
          label="Mot de passe"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={errors.password}
          required
        />
        <Button type="submit" className={formStyles.submit} disabled={submitting}>
          {submitting ? 'Connexion…' : 'Se connecter'}
        </Button>
      </form>
    </AuthLayout>
  )
}
