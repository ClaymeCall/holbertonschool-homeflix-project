import { ButtonLink } from '../components/Button'
import { ErrorState } from '../components/StatusMessage'

export function NotFoundPage() {
  return (
    <>
      <title>Page not found · HomeFlix</title>
      <ErrorState title="Page not found" message="This page does not exist.">
        <ButtonLink to="/" variant="secondary">
          Back to home
        </ButtonLink>
      </ErrorState>
    </>
  )
}
