import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { describeError } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import {
  EMAIL_INVALID,
  PASSWORD_MAX_BYTES,
  PASSWORD_MIN_LENGTH,
  validateSetup,
  type FieldErrors,
} from '../auth/validation'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { TextField } from '../components/TextField'

type Field = 'email' | 'password' | 'confirmation'

/** French text for a field the server rejected (400 `validation_failed`). Its own message is never shown. */
const serverFieldError: Record<'email' | 'password', string> = {
  email: EMAIL_INVALID,
  password: `Ce mot de passe n'est pas accepté. Utilisez au moins ${PASSWORD_MIN_LENGTH} caractères et au plus ${PASSWORD_MAX_BYTES} octets.`,
}

/** First-run setup: creates the first administrator. Only shown while none exists (ACC-01, ACC-02). */
export function SetupPage() {
  const { completeSetup, retry } = useAuth()
  const [values, setValues] = useState({ email: '', password: '', confirmation: '' })
  const [errors, setErrors] = useState<FieldErrors<Field>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [alreadyDone, setAlreadyDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const emailRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)
  const confirmationRef = useRef<HTMLInputElement>(null)

  function update(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateSetup(values)
    setErrors(found)
    setFormError(null)
    if (found.email) return emailRef.current?.focus()
    if (found.password) return passwordRef.current?.focus()
    if (found.confirmation) return confirmationRef.current?.focus()

    setSubmitting(true)
    try {
      await completeSetup({ email: values.email.trim(), password: values.password })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setAlreadyDone(true)
        setFormError('Un administrateur existe déjà. Connectez-vous avec son compte.')
      } else if (error instanceof ApiError && error.code === 'validation_failed' && error.fields.length > 0) {
        const rejected: FieldErrors<Field> = {}
        if (error.fields.includes('email')) rejected.email = serverFieldError.email
        if (error.fields.includes('password')) rejected.password = serverFieldError.password
        setErrors(rejected)
        if (rejected.email) emailRef.current?.focus()
        else if (rejected.password) passwordRef.current?.focus()
        else setFormError(describeError(error))
      } else {
        setFormError(describeError(error))
      }
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Bienvenue sur HomeFlix"
      intro={
        <p>
          Créez le compte administrateur de la maison. Il servira à connecter la bibliothèque de films et à créer les
          comptes des autres membres du foyer.
        </p>
      }
    >
      {formError && (
        <div className={formStyles.alert} role="alert">
          <p>{formError}</p>
        </div>
      )}
      {alreadyDone ? (
        <Button onClick={retry}>Aller à la connexion</Button>
      ) : (
        <form className={formStyles.form} onSubmit={handleSubmit} noValidate aria-busy={submitting}>
          <TextField
            ref={emailRef}
            label="Adresse e-mail"
            hint="Elle servira d'identifiant pour vous connecter."
            name="email"
            type="email"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            value={values.email}
            onChange={(event) => update('email', event.target.value)}
            error={errors.email}
            required
          />
          <TextField
            ref={passwordRef}
            label="Mot de passe"
            hint={`Au moins ${PASSWORD_MIN_LENGTH} caractères. Une phrase facile à retenir fonctionne bien.`}
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
            label="Confirmer le mot de passe"
            name="confirmation"
            type="password"
            autoComplete="new-password"
            value={values.confirmation}
            onChange={(event) => update('confirmation', event.target.value)}
            error={errors.confirmation}
            required
          />
          <Button type="submit" className={formStyles.submit} disabled={submitting}>
            {submitting ? 'Création du compte…' : 'Créer le compte administrateur'}
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
