import { ButtonLink } from '../components/Button'
import { ErrorState } from '../components/StatusMessage'

export function NotFoundPage() {
  return (
    <>
      <title>Page introuvable · HomeFlix</title>
      <ErrorState title="Page introuvable" message="Cette page n'existe pas.">
        <ButtonLink to="/" variant="secondary">
          Retour à l'accueil
        </ButtonLink>
      </ErrorState>
    </>
  )
}
