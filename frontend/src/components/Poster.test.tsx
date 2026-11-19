import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Poster } from './Poster'

describe('Poster', () => {
  it('shows the image without sending a referrer', () => {
    render(<Poster src="https://upload.wikimedia.org/a.jpg" title="The Film" />)
    const image = screen.getByRole('img', { name: 'Poster for The Film' })
    expect(image).toHaveAttribute('referrerpolicy', 'no-referrer')
  })

  it('shows the placeholder when there is no poster', () => {
    render(<Poster src={null} title="The Film" />)
    expect(screen.getByRole('img', { name: 'Poster unavailable for The Film' })).toBeInTheDocument()
  })

  it('falls back to the placeholder when the image fails to load', () => {
    render(<Poster src="https://upload.wikimedia.org/broken.jpg" title="The Film" />)
    fireEvent.error(screen.getByRole('img', { name: 'Poster for The Film' }))
    expect(screen.getByRole('img', { name: 'Poster unavailable for The Film' })).toBeInTheDocument()
  })
})
