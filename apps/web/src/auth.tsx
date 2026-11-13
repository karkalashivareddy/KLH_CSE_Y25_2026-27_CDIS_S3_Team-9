import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, getCurrentUser, login as apiLogin, logout as apiLogout, register as apiRegister, type User } from './api'
import { AuthContext, type AuthContextValue } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    const clearOnUnauthorized = () => { if (active) setUser(null) }
    window.addEventListener('cloudresilience:unauthorized', clearOnUnauthorized)
    getCurrentUser().then((current) => { if (active) setUser(current) })
      .catch((error: unknown) => { if (active && !(error instanceof ApiError && error.status === 401)) setUser(null) })
      .finally(() => { if (active) setLoading(false) })
    return () => {
      active = false
      window.removeEventListener('cloudresilience:unauthorized', clearOnUnauthorized)
    }
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    async login(input) {
      const authenticated = await apiLogin(input)
      setUser(authenticated)
      return authenticated
    },
    async register(input) { return apiRegister(input) },
    async logout() {
      await apiLogout()
      setUser(null)
      window.dispatchEvent(new Event('cloudresilience:logout'))
    },
  }), [user, loading])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
