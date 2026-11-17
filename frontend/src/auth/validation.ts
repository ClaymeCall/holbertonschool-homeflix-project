/**
 * Client-side checks give quick feedback only. The identity service validates
 * every input again (SEC-01). Limits follow the setup request of #125.
 */

export const PASSWORD_MIN_LENGTH = 12
/** BCrypt only uses the first 72 bytes of a password, so the server rejects longer ones. */
export const PASSWORD_MAX_BYTES = 72
const EMAIL_MAX_LENGTH = 254
/** Deliberately loose: the server makes the real check. */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/

export const EMAIL_INVALID = 'Saisissez une adresse e-mail valide, par exemple nom@exemple.fr.'
export const PASSWORD_TOO_LONG = `Le mot de passe est trop long : ${PASSWORD_MAX_BYTES} octets au maximum (une lettre accentuée compte pour deux).`

export type FieldErrors<K extends string> = Partial<Record<K, string>>

export function validateSignIn(values: { email: string; password: string }): FieldErrors<'email' | 'password'> {
  const errors: FieldErrors<'email' | 'password'> = {}
  if (!values.email.trim()) errors.email = 'Saisissez votre adresse e-mail.'
  if (!values.password) errors.password = 'Saisissez votre mot de passe.'
  return errors
}

export function validateSetup(values: {
  email: string
  password: string
  confirmation: string
}): FieldErrors<'email' | 'password' | 'confirmation'> {
  const errors: FieldErrors<'email' | 'password' | 'confirmation'> = {}
  const email = values.email.trim()
  if (!email) {
    errors.email = 'Saisissez une adresse e-mail.'
  } else if (email.length > EMAIL_MAX_LENGTH) {
    errors.email = `L'adresse e-mail est trop longue : ${EMAIL_MAX_LENGTH} caractères au maximum.`
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = EMAIL_INVALID
  }
  if (!values.password) {
    errors.password = 'Choisissez un mot de passe.'
  } else if (values.password.length < PASSWORD_MIN_LENGTH) {
    errors.password = `Le mot de passe doit contenir au moins ${PASSWORD_MIN_LENGTH} caractères.`
  } else if (new TextEncoder().encode(values.password).length > PASSWORD_MAX_BYTES) {
    errors.password = PASSWORD_TOO_LONG
  }
  if (!values.confirmation) {
    errors.confirmation = 'Confirmez le mot de passe.'
  } else if (values.confirmation !== values.password) {
    errors.confirmation = 'Les deux mots de passe ne correspondent pas.'
  }
  return errors
}
