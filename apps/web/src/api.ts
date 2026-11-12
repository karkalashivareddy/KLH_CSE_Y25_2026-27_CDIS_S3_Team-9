export type ApiHealth = { status: string; application?: string }
export type User = { id: string; email: string; firstName: string; lastName: string; roles: Array<'CUSTOMER' | 'ADMIN'> }
export type RegisterInput = { email: string; password: string; firstName: string; lastName: string }
export type LoginInput = { email: string; password: string }

const apiBase = import.meta.env.VITE_API_BASE_URL ?? ''
let csrf: { headerName: string; token: string } | null = null

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
  })
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event('cloudresilience:unauthorized'))
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json() as { message?: string }
      if (body.message) message = body.message
    } catch { /* Keep the status-based message when the response is not JSON. */ }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function csrfHeader(): Promise<Record<string, string>> {
  if (!csrf) csrf = await request('/api/v1/auth/csrf') as { headerName: string; token: string }
  return { [csrf.headerName]: csrf.token }
}

async function mutate<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', headers: await csrfHeader(), ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
}

export function getApiHealth(): Promise<ApiHealth> {
  return request('/api/v1/health', { signal: AbortSignal.timeout(3000) })
}

export function getCurrentUser(): Promise<User> {
  return request('/api/v1/auth/me')
}

export function register(input: RegisterInput): Promise<User> {
  return mutate('/api/v1/auth/register', input)
}

export async function login(input: LoginInput): Promise<User> {
  const user = await mutate<User>('/api/v1/auth/login', input)
  csrf = null // The server rotates the CSRF token at authentication boundaries.
  return user
}

export async function logout(): Promise<void> {
  await mutate<void>('/api/v1/auth/logout')
  csrf = null
}

export function checkAdminAccess(): Promise<{ authorization: string }> {
  return request('/api/v1/admin/access-check')
}
