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

export const EMAIL_INVALID = 'Enter a valid email address, for example name@example.com.'
export const PASSWORD_TOO_LONG = `The password is too long: ${PASSWORD_MAX_BYTES} bytes at most (an accented letter counts as two).`

export type FieldErrors<K extends string> = Partial<Record<K, string>>

export function validateSignIn(values: { email: string; password: string }): FieldErrors<'email' | 'password'> {
  const errors: FieldErrors<'email' | 'password'> = {}
  if (!values.email.trim()) errors.email = 'Enter your email address.'
  if (!values.password) errors.password = 'Enter your password.'
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
    errors.email = 'Enter an email address.'
  } else if (email.length > EMAIL_MAX_LENGTH) {
    errors.email = `The email address is too long: ${EMAIL_MAX_LENGTH} characters at most.`
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = EMAIL_INVALID
  }
  if (!values.password) {
    errors.password = 'Choose a password.'
  } else if (values.password.length < PASSWORD_MIN_LENGTH) {
    errors.password = `The password must be at least ${PASSWORD_MIN_LENGTH} characters long.`
  } else if (new TextEncoder().encode(values.password).length > PASSWORD_MAX_BYTES) {
    errors.password = PASSWORD_TOO_LONG
  }
  if (!values.confirmation) {
    errors.confirmation = 'Confirm the password.'
  } else if (values.confirmation !== values.password) {
    errors.confirmation = 'The two passwords do not match.'
  }
  return errors
}
