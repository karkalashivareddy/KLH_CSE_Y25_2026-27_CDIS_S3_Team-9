import { Component, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, checkAdminAccess, getApiHealth, type ApiHealth } from './api'
import { AuthProvider } from './auth'
import { useAuth } from './auth-context'
import { AdminCatalogPage, CatalogPage, ProductDetailPage } from './catalog'
import { CartPage, CartProvider, useCart } from './cart'
import { AdminOrderDetailPage, AdminOrdersPage, CheckoutPage, OrderDetailPage, OrderHistoryPage } from './orders'

const links = [
  { href: '/', label: 'Overview', icon: '◫' },
  { href: '/architecture', label: 'Architecture', icon: '⌘' },
  { href: '/recovery', label: 'Recovery planning', icon: '↗' },
  { href: '/catalog', label: 'Product catalog', icon: '▦' },
]

export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() { return { failed: true } }

  render() {
    if (this.state.failed) return <main className="error-screen"><span className="eyebrow">WORKSPACE</span><h1>Something went wrong</h1><p>The page could not be displayed. Reload to try again.</p><button onClick={() => window.location.reload()}>Reload workspace</button></main>
    return this.props.children
  }
}

function Shell({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { user, loading, logout } = useAuth()
  const { cart } = useCart()
  const [logoutError, setLogoutError] = useState('')
  async function handleLogout() {
    setLogoutError('')
    try { await logout() } catch { setLogoutError('Could not end the session. Please try again.') }
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <Link className="brand" to="/" aria-label="CloudResilience home"><span className="brand-mark">C</span><span>cloud<span className="brand-light">resilience</span></span></Link>
      <p className="nav-caption">WORKSPACE</p>
      <nav aria-label="Main navigation">{links.map((item) => <Link key={item.href} className={`nav-link ${location.pathname === item.href ? 'active' : ''}`} to={item.href}><span aria-hidden="true">{item.icon}</span>{item.label}</Link>)}</nav>
      <p className="nav-caption auth-nav-caption">ACCOUNT</p>
      <nav aria-label="Account navigation">
          {user ? <><Link className={`nav-link ${location.pathname === '/account' ? 'active' : ''}`} to="/account"><span aria-hidden="true">◎</span>My account</Link>{user.roles.includes('CUSTOMER')&&<><Link className={`nav-link ${location.pathname === '/cart' ? 'active' : ''}`} to="/cart"><span aria-hidden="true">🛒</span>Cart <span className="nav-cart-badge">{cart?.totalQuantity ?? 0}</span></Link><Link className={`nav-link ${location.pathname.startsWith('/orders') ? 'active' : ''}`} to="/orders"><span aria-hidden="true">▤</span>My orders</Link></>}{user.roles.includes('ADMIN')&&<><Link className={`nav-link ${location.pathname.startsWith('/admin/catalog') ? 'active' : ''}`} to="/admin/catalog"><span aria-hidden="true">⌑</span>Admin catalog</Link><Link className={`nav-link ${location.pathname.startsWith('/admin/orders') ? 'active' : ''}`} to="/admin/orders"><span aria-hidden="true">▤</span>Orders</Link></>}<button className="nav-link nav-button" onClick={handleLogout} disabled={loading}><span aria-hidden="true">↪</span>Sign out</button></> : <><Link className="nav-link" to="/login"><span aria-hidden="true">↪</span>Sign in</Link><Link className="nav-link" to="/register"><span aria-hidden="true">＋</span>Create account</Link></>}
      </nav>
      {logoutError && <p className="nav-error" role="alert">{logoutError}</p>}
       <div className="sidebar-note"><span className="note-dot" />Phase 4 foundation<br /><small>Local development</small></div>
      <div className="profile"><span className="avatar">{user ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}` : '—'}</span><span><strong>{user ? `${user.firstName} ${user.lastName}` : 'Not signed in'}</strong><small>{user?.email ?? 'Guest session'}</small></span></div>
    </aside>
    <main className="main-area"><header className="topbar"><span className="breadcrumb">CloudResilience <span>/</span> <strong>{links.find((item) => item.href === location.pathname)?.label ?? 'Account'}</strong></span><span className="env-pill"><i /> LOCAL FOUNDATION</span></header>{children}</main>
  </div>
}

function Overview() {
  const [health, setHealth] = useState<ApiHealth | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { let active = true; getApiHealth().then((result) => { if (active) setHealth(result) }).catch(() => { if (active) setError('unavailable') }); return () => { active = false } }, [])
  return <div className="page-content">
    <section className="welcome"><div><span className="eyebrow">ENGINEERING WORKSPACE</span><h1>Build resilience<br /><span>by design.</span></h1><p>A secure, cost-conscious foundation for exploring cloud-native commerce and disaster recovery.</p></div><div className="welcome-art" aria-hidden="true"><div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="core">CR</div><span className="orbit-node node-a">AWS</span><span className="orbit-node node-b">AZ</span><span className="orbit-node node-c">GCP</span></div></section>
    <div className="section-heading"><div><h2>Foundation status</h2><p>Real application connectivity only; no cloud telemetry is shown.</p></div><span className="status-label"><i /> DEVELOPMENT</span></div>
    <section className="status-grid" aria-label="Foundation status">
      <article className="status-card"><div className="card-top"><span className="status-icon blue">API</span><span className="tag">BACKEND</span></div><h3>Application API</h3><p role="status" aria-live="polite">{health ? `Health endpoint: ${health.status}` : error ? 'API is not reachable' : 'Checking local API…'}</p><div className={`service-state ${health ? 'up' : error ? 'down' : ''}`}><i />{health ? 'Connected' : error ? 'Unavailable' : 'Checking'}</div></article>
      <article className="status-card"><div className="card-top"><span className="status-icon teal">DB</span><span className="tag">STATE SERVICES</span></div><h3>Data services</h3><p>PostgreSQL owns durable business data and carts; Redis stores shared HTTP sessions.</p><div className="service-state planned"><i />Runtime status: use API readiness</div></article>
      <article className="status-card"><div className="card-top"><span className="status-icon violet">☁</span><span className="tag">CLOUD</span></div><h3>Cloud providers</h3><p>No cloud account is connected in this phase.</p><div className="service-state planned"><i />Not connected</div></article>
    </section>
    <section className="callout"><span className="callout-mark">✳</span><div><strong>Designed for deliberate resilience</strong><p>Identity is protected by server-side sessions, backend authorization, and CSRF defenses.</p></div><Link to="/register">Create a customer account <span aria-hidden="true">→</span></Link></section>
     <footer className="page-footer">CloudResilience · Team 9 <span>Phase 4 · Distributed state</span></footer>
  </div>
}

function InformationPage({ title, copy }: { title: string; copy: string }) { return <div className="page-content simple-page"><span className="eyebrow">PHASE 2 FOUNDATION</span><h1>{title}</h1><p>{copy}</p><p className="planned-banner">This capability is planned, not implemented. No simulated operational data is presented.</p></div> }

function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return <div className="page-content"><section className="auth-card"><span className="eyebrow">CLOUDRESILIENCE ACCOUNT</span><h1>{title}</h1>{children}</section></div>
}

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const notice = (location.state as { notice?: string } | null)?.notice
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setPending(true)
    try { await login({ email, password }); const from = (location.state as { from?: string } | null)?.from; navigate(from ?? '/account', { replace: true }) }
    catch (reason) { setError(reason instanceof ApiError && reason.status === 401 ? 'Email or password was not accepted.' : reason instanceof Error ? reason.message : 'Sign in failed.') }
    finally { setPending(false) }
  }
  return <AuthLayout title="Sign in">
    {notice && <p className="form-notice" role="status">{notice}</p>}
    <form className="auth-form" onSubmit={submit}>
      <label htmlFor="login-email">Email</label><input id="login-email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} />
      <label htmlFor="login-password">Password</label><input id="login-password" type="password" autoComplete="current-password" required maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} />
      {error && <p className="form-error" role="alert">{error}</p>}
      <button type="submit" disabled={pending}>{pending ? 'Signing in…' : 'Sign in'}</button>
    </form>
    <p className="auth-switch">New here? <Link to="/register">Create an account</Link></p>
  </AuthLayout>
}

function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState({ firstName: '', lastName: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setPending(true)
    try {
      await register(values)
      navigate('/login', { replace: true, state: { notice: 'Account created. Sign in to continue.' } })
    } catch (reason) {
      setError(reason instanceof ApiError && reason.status === 409 ? 'An account with this email already exists.' : reason instanceof Error ? reason.message : 'Account creation failed.')
    } finally { setPending(false) }
  }
  return <AuthLayout title="Create customer account">
    <form className="auth-form" onSubmit={submit}>
      <label htmlFor="register-first-name">First name</label><input id="register-first-name" autoComplete="given-name" required maxLength={80} value={values.firstName} onChange={(event) => setValues({ ...values, firstName: event.target.value })} />
      <label htmlFor="register-last-name">Last name</label><input id="register-last-name" autoComplete="family-name" required maxLength={80} value={values.lastName} onChange={(event) => setValues({ ...values, lastName: event.target.value })} />
      <label htmlFor="register-email">Email</label><input id="register-email" type="email" autoComplete="email" required maxLength={254} value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} />
      <label htmlFor="register-password">Password</label><input id="register-password" type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={values.password} onChange={(event) => setValues({ ...values, password: event.target.value })} /><small className="field-hint">At least 12 characters. Use a unique passphrase.</small>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button type="submit" disabled={pending}>{pending ? 'Creating account…' : 'Create account'}</button>
    </form>
    <p className="auth-switch">Already registered? <Link to="/login">Sign in</Link></p>
  </AuthLayout>
}

function ProtectedRoute({ children, requiredRole }: { children: ReactNode; requiredRole?: 'ADMIN' | 'CUSTOMER' }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <AuthLayout title="Checking your session"><p role="status">Verifying current account…</p></AuthLayout>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (requiredRole && !user.roles.includes(requiredRole)) return <AuthLayout title="Access denied"><p role="alert">{requiredRole === 'ADMIN' ? 'Your account does not have administrator access.' : 'Your account does not have customer access.'}</p><Link to="/account">Return to your account</Link></AuthLayout>
  return children
}

function AccountPage() {
  const { user } = useAuth()
  if (!user) return null
  return <AuthLayout title="Your account"><p className="account-lead">You are signed in with an active server-managed session.</p><dl className="account-details"><div><dt>Name</dt><dd>{user.firstName} {user.lastName}</dd></div><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Roles</dt><dd>{user.roles.join(', ')}</dd></div></dl></AuthLayout>
}

function AdminPage() {
  const [result, setResult] = useState('Checking backend authorization…')
  useEffect(() => { let active = true; checkAdminAccess().then((value) => { if (active) setResult(value.authorization) }).catch((error: unknown) => { if (active) setResult(error instanceof ApiError && error.status === 403 ? 'The backend denied administrator access.' : 'Administrator authorization could not be verified.') }); return () => { active = false } }, [])
  return <AuthLayout title="Administrator check"><p role="status">{result}</p><p className="field-hint">This display follows the API authorization response; frontend routing is not an access-control boundary.</p></AuthLayout>
}

export default function App() {
  return <AuthProvider><CartProvider><ErrorBoundary><Shell><Routes>
    <Route path="/" element={<Overview />} />
    <Route path="/architecture" element={<InformationPage title="Architecture" copy="The target architecture and design decisions are maintained in the repository documentation." />} />
     <Route path="/recovery" element={<InformationPage title="Recovery planning" copy="Recovery objectives and workflows are being defined; no recovery automation is available." />} />
    <Route path="/catalog" element={<CatalogPage />} />
    <Route path="/products/:id" element={<ProductDetailPage />} />
     <Route path="/cart" element={<ProtectedRoute requiredRole="CUSTOMER"><CartPage /></ProtectedRoute>} />
     <Route path="/checkout" element={<ProtectedRoute requiredRole="CUSTOMER"><CheckoutPage /></ProtectedRoute>} />
     <Route path="/orders" element={<ProtectedRoute requiredRole="CUSTOMER"><OrderHistoryPage /></ProtectedRoute>} />
     <Route path="/orders/:id" element={<ProtectedRoute requiredRole="CUSTOMER"><OrderDetailPage /></ProtectedRoute>} />
     <Route path="/admin/catalog" element={<ProtectedRoute requiredRole="ADMIN"><AdminCatalogPage /></ProtectedRoute>} />
     <Route path="/admin/orders" element={<ProtectedRoute requiredRole="ADMIN"><AdminOrdersPage /></ProtectedRoute>} />
     <Route path="/admin/orders/:id" element={<ProtectedRoute requiredRole="ADMIN"><AdminOrderDetailPage /></ProtectedRoute>} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route path="/account" element={<ProtectedRoute><AccountPage /></ProtectedRoute>} />
    <Route path="/admin" element={<ProtectedRoute requiredRole="ADMIN"><AdminPage /></ProtectedRoute>} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></Shell></ErrorBoundary></CartProvider></AuthProvider>
}
