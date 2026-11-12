export type ApiHealth = { status: string; application?: string }
export type User = { id: string; email: string; firstName: string; lastName: string; roles: Array<'CUSTOMER' | 'ADMIN'> }
export type RegisterInput = { email: string; password: string; firstName: string; lastName: string }
export type LoginInput = { email: string; password: string }
export type Product = { id: string; sku: string; name: string; slug: string; short_description?: string; description?: string; price: number; currency: string; category_id: string; category_name: string; image_url?: string; status?: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'; available_quantity: number }
export type ProductPage = { content: Product[]; page: number; size: number; totalElements: number; totalPages: number }
export type Category = { id: string; name: string; slug: string; description?: string; active: boolean }

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

async function write<T>(path: string, method: 'PUT' | 'PATCH', body: unknown): Promise<T> {
  return request<T>(path, { method, headers: await csrfHeader(), body: JSON.stringify(body) })
}

export function getProducts(params: { page?: number; size?: number; q?: string; categoryId?: string } = {}): Promise<ProductPage> {
  const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 12), sort: 'name', direction: 'asc' })
  if (params.q) query.set('q', params.q)
  if (params.categoryId) query.set('categoryId', params.categoryId)
  return request(`/api/v1/products?${query}`)
}
export function getAdminProducts(): Promise<Product[]> { return request('/api/v1/admin/products') }
export function getAdminCategories(): Promise<Category[]> { return request('/api/v1/admin/categories') }
export function getProduct(id: string): Promise<Product> { return request(`/api/v1/products/${id}`) }
export function getCategories(): Promise<Category[]> { return request('/api/v1/categories') }
export function createProduct(input: Record<string, unknown>): Promise<Product> { return mutate('/api/v1/admin/products', input) }
export function createCategory(input: Record<string, unknown>): Promise<Category> { return mutate('/api/v1/admin/categories', input) }
export function updateCategory(id: string, input: Record<string, unknown>): Promise<Category> { return write(`/api/v1/admin/categories/${id}`, 'PUT', input) }
export function updateProduct(id: string, input: Record<string, unknown>): Promise<Product> { return write(`/api/v1/admin/products/${id}`, 'PUT', input) }
export function updateProductStatus(id: string, status: string): Promise<Product> { return write(`/api/v1/admin/products/${id}/status`, 'PATCH', { status }) }
export function initializeInventory(id: string, quantity: number, reason: string): Promise<unknown> { return mutate(`/api/v1/admin/inventory/${id}`, { quantity, reason }) }
export function adjustInventory(id: string, delta: number, reason: string): Promise<unknown> { return mutate(`/api/v1/admin/inventory/${id}/adjustments`, { delta, reason }) }
export function reserveInventory(id: string, quantity: number, reason: string): Promise<{ reservationId: string; inventory: unknown }> { return mutate(`/api/v1/admin/inventory/${id}/reservations`, { quantity, reason }) }
export function releaseInventory(id: string, reservationId: string, reason: string): Promise<unknown> { return mutate(`/api/v1/admin/inventory/${id}/reservations/${reservationId}/release`, { reason }) }
export function getInventory(id: string): Promise<{ inventory: { quantity_on_hand: number; quantity_reserved: number; available_quantity: number }; movements: Array<Record<string, unknown>> }> { return request(`/api/v1/admin/inventory/${id}`) }

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
