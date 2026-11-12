import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App, { ErrorBoundary } from './App'

describe('CloudResilience workspace', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('shows foundation status without claiming cloud telemetry', () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    render(<MemoryRouter><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: /build resilience/i })).toBeInTheDocument()
    expect(screen.getByText(/no cloud telemetry is shown/i)).toBeInTheDocument()
    expect(screen.getByText(/not connected/i)).toBeInTheDocument()
  })

  it('shows an explicit loading state while the API request is pending', () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => undefined)))
    render(<MemoryRouter><App /></MemoryRouter>)

    expect(screen.getByText('Checking local API…')).toBeInTheDocument()
  })

  it('shows a disconnected state when the API request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    render(<MemoryRouter><App /></MemoryRouter>)

    await waitFor(() => expect(screen.getByText('API is not reachable')).toBeInTheDocument())
  })

  it('shows a recovery screen when a child render throws', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    function BrokenChild(): never { throw new Error('test render error') }

    render(<ErrorBoundary><BrokenChild /></ErrorBoundary>)

    expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload workspace' })).toBeInTheDocument()
  })
})
