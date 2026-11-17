import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from './test/render'

describe('App', () => {
  it('shows the HomeFlix logo linking to the home page', () => {
    renderApp('/')
    expect(screen.getByRole('link', { name: 'HomeFlix, accueil' })).toHaveAttribute('href', '/')
  })

  it('offers a skip link to the main content', () => {
    renderApp('/')
    expect(screen.getByRole('link', { name: 'Aller au contenu' })).toHaveAttribute('href', '#contenu')
  })

  it('shows a not found page for unknown paths', () => {
    renderApp('/nowhere')
    expect(screen.getByRole('heading', { name: 'Page introuvable' })).toBeInTheDocument()
  })
})
