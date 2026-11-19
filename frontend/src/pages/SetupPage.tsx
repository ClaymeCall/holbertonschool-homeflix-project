import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { describeError } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import {
  PASSWORD_MIN_LENGTH,
  validateSetup,
  type FieldErrors,
} from '../auth/validation'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { TextField } from '../components/TextField'

type Field = 'email' | 'password' | 'confirmation'

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
        setFormError(describeError(error))
      } else if (error instanceof ApiError && error.code === 'validation_failed' && (error.fields.email || error.fields.password)) {
        // The server's own message for each rejected field (ADR-0021).
        const rejected: FieldErrors<Field> = { email: error.fields.email, password: error.fields.password }
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
      title="Welcome to HomeFlix"
      intro={
        <p>
          Create the administrator account for your household. You will use it to connect the film library and to
          create accounts for the other members of the household.
        </p>
      }
    >
      {formError && (
        <div className={formStyles.alert} role="alert">
          <p>{formError}</p>
        </div>
      )}
      {alreadyDone ? (
        <Button onClick={retry}>Go to sign-in</Button>
      ) : (
        <form className={formStyles.form} onSubmit={handleSubmit} noValidate aria-busy={submitting}>
          <TextField
            ref={emailRef}
            label="Email address"
            hint="You will use it to sign in."
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
            label="Password"
            hint={`At least ${PASSWORD_MIN_LENGTH} characters. A phrase that is easy to remember works well.`}
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
          <Button type="submit" className={formStyles.submit} disabled={submitting}>
            {submitting ? 'Creating the account…' : 'Create the administrator account'}
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
