import { afterEach, describe, expect, it, vi } from 'vitest'
import { getApiHealth } from './api'

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

    await expect(getApiHealth()).rejects.toThrow('Health request failed (503)')
  })
})
