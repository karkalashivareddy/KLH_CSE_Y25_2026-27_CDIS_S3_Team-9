import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const customer = { id: 'customer-id', email: 'ada@example.com', firstName: 'Ada', lastName: 'Lovelace', roles: ['CUSTOMER'] }
const response = (status: number, body?: unknown) => new Response(body === undefined ? null : JSON.stringify(body), {
  status,
  headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('authentication UI', () => {
  it('registers a customer and directs them to sign in without claiming a session', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>((input) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return Promise.resolve(response(401, { message: 'Authentication required' }))
      if (url.endsWith('/auth/csrf')) return Promise.resolve(response(200, { headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      if (url.endsWith('/auth/register')) return Promise.resolve(response(201, customer))
      return Promise.resolve(response(200, { status: 'UP' }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<MemoryRouter initialEntries={['/register']}><App /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Ada' } })
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Lovelace' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ada@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'a-unique-long-passphrase' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Account created. Sign in to continue.')
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/register'))).toBe(true)
  })

  it('shows a real authentication failure and a loading state while signing in', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>((input) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return Promise.resolve(response(401, { message: 'Authentication required' }))
      if (url.endsWith('/auth/csrf')) return Promise.resolve(response(200, { headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      if (url.endsWith('/auth/login')) return Promise.resolve(response(401, { message: 'Invalid email or password' }))
      return Promise.resolve(response(200, { status: 'UP' }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ada@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'incorrect-passphrase' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password was not accepted.')
    expect(fetchMock.mock.calls.find(([url]) => String(url).endsWith('/auth/login'))?.[1]).toEqual(expect.objectContaining({ method: 'POST', credentials: 'same-origin' }))
  })

  it('disables sign in while the server request is still pending', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return Promise.resolve(response(401, { message: 'Authentication required' }))
      if (url.endsWith('/auth/csrf')) return Promise.resolve(response(200, { headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      if (url.endsWith('/auth/login')) return new Promise<Response>(() => undefined)
      return Promise.resolve(response(200, { status: 'UP' }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ada@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'a-unique-long-passphrase' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('button', { name: 'Signing in…' })).toBeDisabled()
  })

  it('loads an authenticated profile and ends the server session on logout', async () => {
    let authenticated = false
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return Promise.resolve(authenticated ? response(200, customer) : response(401, { message: 'Authentication required' }))
      if (url.endsWith('/auth/csrf')) return Promise.resolve(response(200, { headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
      if (url.endsWith('/auth/login')) { authenticated = true; return Promise.resolve(response(200, customer)) }
      if (url.endsWith('/auth/logout')) { authenticated = false; return Promise.resolve(response(204)) }
      return Promise.resolve(response(200, { status: 'UP' }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: customer.email } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'a-unique-long-passphrase' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('ada@example.com')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument())
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/logout'))).toBe(true)
  })

  it('does not show the protected admin page to a customer', async () => {
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => String(input).endsWith('/auth/me')
      ? Promise.resolve(response(200, customer))
      : Promise.resolve(response(200, { status: 'UP' }))))
    render(<MemoryRouter initialEntries={['/admin']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Your account does not have administrator access.')
  })

  it('redirects an anonymous user from the protected account route', async () => {
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => String(input).endsWith('/auth/me')
      ? Promise.resolve(response(401, { message: 'Authentication required' }))
      : Promise.resolve(response(200, { status: 'UP' }))))
    render(<MemoryRouter initialEntries={['/account']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
