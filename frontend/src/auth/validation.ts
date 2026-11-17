/**
 * Client-side checks give quick feedback only. The identity service validates
 * every input again (SEC-01). To align with the contract once it is written.
 */

export const PASSWORD_MIN_LENGTH = 12
const PASSWORD_MAX_LENGTH = 128
const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,32}$/

export type FieldErrors<K extends string> = Partial<Record<K, string>>

export function validateSignIn(values: { username: string; password: string }): FieldErrors<'username' | 'password'> {
  const errors: FieldErrors<'username' | 'password'> = {}
  if (!values.username.trim()) errors.username = "Saisissez votre nom d'utilisateur."
  if (!values.password) errors.password = 'Saisissez votre mot de passe.'
  return errors
}

export function validateSetup(values: {
  username: string
  password: string
  confirmation: string
}): FieldErrors<'username' | 'password' | 'confirmation'> {
  const errors: FieldErrors<'username' | 'password' | 'confirmation'> = {}
  const username = values.username.trim()
  if (!username) {
    errors.username = "Choisissez un nom d'utilisateur."
  } else if (!USERNAME_PATTERN.test(username)) {
    errors.username = 'Utilisez 3 à 32 caractères : lettres sans accent, chiffres, point, tiret ou tiret bas.'
  }
  if (!values.password) {
    errors.password = 'Choisissez un mot de passe.'
  } else if (values.password.length < PASSWORD_MIN_LENGTH) {
    errors.password = `Le mot de passe doit contenir au moins ${PASSWORD_MIN_LENGTH} caractères.`
  } else if (values.password.length > PASSWORD_MAX_LENGTH) {
    errors.password = `Le mot de passe doit contenir au plus ${PASSWORD_MAX_LENGTH} caractères.`
  }
  if (!values.confirmation) {
    errors.confirmation = 'Confirmez le mot de passe.'
  } else if (values.confirmation !== values.password) {
    errors.confirmation = 'Les deux mots de passe ne correspondent pas.'
  }
  return errors
}
