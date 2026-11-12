import { afterEach, describe, expect, it, vi } from 'vitest'
import { getApiHealth, login, logout } from './api'

describe('getApiHealth', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests the same-origin health endpoint and returns its payload', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP', application: 'cloudresilience-api' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getApiHealth()).resolves.toEqual({ status: 'UP', application: 'cloudresilience-api' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/health', expect.objectContaining({ headers: { Accept: 'application/json' } }))
  })

  it('rejects non-success responses with the HTTP status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))

    await expect(getApiHealth()).rejects.toThrow('Request failed (503)')
  })

  it('uses a server-provided CSRF token for session login and logout', async () => {
    const user = { id: 'id-1', email: 'user@example.com', firstName: 'Ada', lastName: 'Lovelace', roles: ['CUSTOMER'] }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-1' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(user), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-2' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(login({ email: 'user@example.com', password: 'a-long-passphrase' })).resolves.toEqual(user)
    await expect(logout()).resolves.toBeUndefined()
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({ method: 'POST', credentials: 'same-origin', headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-1' }) }))
    expect(fetchMock.mock.calls[3][1]).toEqual(expect.objectContaining({ method: 'POST', credentials: 'same-origin', headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-2' }) }))
  })
})
