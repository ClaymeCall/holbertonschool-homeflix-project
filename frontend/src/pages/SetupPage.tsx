import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { describeError } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { PASSWORD_MIN_LENGTH, validateSetup, type FieldErrors } from '../auth/validation'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { TextField } from '../components/TextField'

type Field = 'username' | 'password' | 'confirmation'

/** First-run setup: creates the first administrator. Only shown while none exists (ACC-01, ACC-02). */
export function SetupPage() {
  const { completeSetup, retry } = useAuth()
  const [values, setValues] = useState({ username: '', password: '', confirmation: '' })
  const [errors, setErrors] = useState<FieldErrors<Field>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [alreadyDone, setAlreadyDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const usernameRef = useRef<HTMLInputElement>(null)
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
    if (found.username) return usernameRef.current?.focus()
    if (found.password) return passwordRef.current?.focus()
    if (found.confirmation) return confirmationRef.current?.focus()

    setSubmitting(true)
    try {
      await completeSetup({ username: values.username.trim(), password: values.password })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setAlreadyDone(true)
        setFormError('Un administrateur existe déjà. Connectez-vous avec son compte.')
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
            ref={usernameRef}
            label="Nom d'utilisateur"
            hint="3 à 32 caractères : lettres sans accent, chiffres, point, tiret ou tiret bas."
            name="username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            value={values.username}
            onChange={(event) => update('username', event.target.value)}
            error={errors.username}
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
