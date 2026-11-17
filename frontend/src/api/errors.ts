import { ApiError } from './client'

/** Plain-language message for an API failure, with no technical details (SEC-04, UX-05). */
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Une erreur inattendue est survenue. Réessayez dans un instant.'
  }
  switch (error.status) {
    case 0:
      return 'Impossible de joindre HomeFlix. Vérifiez votre connexion puis réessayez.'
    case 400:
    case 422:
      return 'Certaines informations sont invalides. Vérifiez-les puis réessayez.'
    case 401:
      return 'Votre session a expiré. Reconnectez-vous pour continuer.'
    case 403:
      return "Vous n'avez pas accès à cette page."
    case 404:
      return "Cet élément est introuvable. Il a peut-être été retiré."
    case 503:
      return 'Ce service est momentanément indisponible. Réessayez dans un instant.'
    default:
      return 'Une erreur est survenue de notre côté. Réessayez dans un instant.'
  }
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
