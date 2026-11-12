import { Component, useEffect, useState } from 'react'
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { getApiHealth, type ApiHealth } from './api'

const links = [
  { href: '/', label: 'Overview', icon: '◫' },
  { href: '/architecture', label: 'Architecture', icon: '⌘' },
  { href: '/recovery', label: 'Recovery planning', icon: '↗' },
]

export class ErrorBoundary extends Component<{ children: React.ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() { return { failed: true } }

  render() {
    if (this.state.failed) return <main className="error-screen"><span className="eyebrow">WORKSPACE</span><h1>Something went wrong</h1><p>The page could not be displayed. Reload to try again.</p><button onClick={() => window.location.reload()}>Reload workspace</button></main>
    return this.props.children
  }
}

function Shell({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  return <div className="app-shell">
    <aside className="sidebar">
      <Link className="brand" to="/" aria-label="CloudResilience home"><span className="brand-mark">C</span><span>cloud<span className="brand-light">resilience</span></span></Link>
      <p className="nav-caption">WORKSPACE</p>
      <nav aria-label="Main navigation">{links.map((item) => <Link key={item.href} className={`nav-link ${location.pathname === item.href ? 'active' : ''}`} to={item.href}><span aria-hidden="true">{item.icon}</span>{item.label}</Link>)}</nav>
      <div className="sidebar-note"><span className="note-dot" />Phase 1 foundation<br /><small>Local development</small></div>
      <div className="profile"><span className="avatar">T9</span><span><strong>Team 9</strong><small>Engineering workspace</small></span></div>
    </aside>
    <main className="main-area"><header className="topbar"><span className="breadcrumb">CloudResilience <span>/</span> <strong>{links.find((item) => item.href === location.pathname)?.label ?? 'Workspace'}</strong></span><span className="env-pill"><i /> LOCAL FOUNDATION</span></header>{children}</main>
  </div>
}

function Overview() {
  const [health, setHealth] = useState<ApiHealth | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { let active = true; getApiHealth().then((result) => { if (active) setHealth(result) }).catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : 'API status unavailable') }); return () => { active = false } }, [])
  return <div className="page-content">
    <section className="welcome"><div><span className="eyebrow">ENGINEERING WORKSPACE</span><h1>Build resilience<br /><span>by design.</span></h1><p>A secure, cost-conscious foundation for exploring cloud-native commerce and disaster recovery.</p></div><div className="welcome-art" aria-hidden="true"><div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="core">CR</div><span className="orbit-node node-a">AWS</span><span className="orbit-node node-b">AZ</span><span className="orbit-node node-c">GCP</span></div></section>
    <div className="section-heading"><div><h2>Foundation status</h2><p>Real application connectivity only; no cloud telemetry is shown.</p></div><span className="status-label"><i /> DEVELOPMENT</span></div>
    <section className="status-grid" aria-label="Foundation status">
      <article className="status-card"><div className="card-top"><span className="status-icon blue">API</span><span className="tag">BACKEND</span></div><h3>Application API</h3><p role="status" aria-live="polite">{health ? `Health endpoint: ${health.status}` : error ? 'API is not reachable' : 'Checking local API…'}</p><div className={`service-state ${health ? 'up' : error ? 'down' : ''}`}><i />{health ? 'Connected' : error ? 'Unavailable' : 'Checking'}</div></article>
      <article className="status-card"><div className="card-top"><span className="status-icon teal">DB</span><span className="tag">LOCAL SERVICE</span></div><h3>Data services</h3><p>PostgreSQL and Redis are planned for local Compose.</p><div className="service-state planned"><i />Not configured</div></article>
      <article className="status-card"><div className="card-top"><span className="status-icon violet">☁</span><span className="tag">CLOUD</span></div><h3>Cloud providers</h3><p>No cloud account is connected in this phase.</p><div className="service-state planned"><i />Not connected</div></article>
    </section>
    <section className="callout"><span className="callout-mark">✳</span><div><strong>Designed for deliberate resilience</strong><p>Start with clear service boundaries, measurable recovery objectives, and cost-aware infrastructure decisions.</p></div><Link to="/architecture">Explore the architecture <span aria-hidden="true">→</span></Link></section>
    <footer className="page-footer">CloudResilience · Team 9 <span>Phase 1 · Local foundation only</span></footer>
  </div>
}

function InformationPage({ title, copy }: { title: string; copy: string }) { return <div className="page-content simple-page"><span className="eyebrow">PHASE 1 FOUNDATION</span><h1>{title}</h1><p>{copy}</p><p className="planned-banner">This capability is planned, not implemented. No simulated operational data is presented.</p></div> }

export default function App() {
  return <ErrorBoundary><Shell><Routes><Route path="/" element={<Overview />} /><Route path="/architecture" element={<InformationPage title="Architecture" copy="The target architecture and design decisions are maintained in the repository documentation." />} /><Route path="/recovery" element={<InformationPage title="Recovery planning" copy="Disaster recovery objectives and workflows are being defined; no recovery automation is available yet." />} /><Route path="*" element={<Navigate to="/" replace />} /></Routes></Shell></ErrorBoundary>
}
