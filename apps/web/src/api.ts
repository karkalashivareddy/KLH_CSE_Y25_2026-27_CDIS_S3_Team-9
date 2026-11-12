export type ApiHealth = { status: string; application?: string }

const apiBase = import.meta.env.VITE_API_BASE_URL ?? ''

export async function getApiHealth(): Promise<ApiHealth> {
  const response = await fetch(`${apiBase}/api/v1/health`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(3000) })
  if (!response.ok) throw new Error(`Health request failed (${response.status})`)
  return response.json() as Promise<ApiHealth>
}
