import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useSchool } from '../../context/SchoolContext'
import { useTheme } from '../../context/ThemeContext'
import { Input, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

const DEMO = [
  { role: 'Super Admin', email: 'admin@techschool.edu', pass: 'Admin@1234', color: '#ef4444', desc: 'Full system control — manage users, cohorts, devices, and view analytics.' },
  { role: 'Facilitator', email: 'james.obi@techschool.edu', pass: 'Fac@1234', color: '#f59e0b', desc: 'Generate QR sessions, mark attendance, review excuse requests.' },
  { role: 'Student',     email: 'ada.okafor@techschool.edu', pass: 'Student@1234', color: '#22c55e', desc: 'Scan QR codes, view attendance history, submit excuse requests.' },
]

export default function TesterPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const { dark, toggle } = useTheme()
  const { settings } = useSchool()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async e => {
    e.preventDefault()
    if (!form.email || !form.password) { setError('Please fill in all fields'); return }
    setError(''); setLoading(true)
    try {
      const data = await login(form.email, form.password)
      toast.success(`Welcome back, ${data.name}!`)
      const routes = { STUDENT: '/student', FACILITATOR: '/facilitator', SUPER_ADMIN: '/admin' }
      navigate(routes[data.role] || '/')
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  const fill = (email, password) => setForm({ email, password })

  return (
    <div style={{ minHeight: '100vh', background: 'var(--off)', display: 'flex', flexDirection: 'column' }}>
      {/* Top bar */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '12px 24px', borderBottom: '1px solid var(--gray-100)', background: 'var(--white)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 32, height: 32, background: 'var(--red)', borderRadius: 7, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/></svg>
          </div>
          <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--gray-900)' }}>QR Attendance · Tester Access</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Link to="/" style={{ fontSize: 12, color: 'var(--gray-500)', textDecoration: 'none', padding: '6px 12px', borderRadius: 6, border: '1px solid var(--gray-200)' }}>
            Home
          </Link>
          <button onClick={toggle} title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 4, padding: '5px 10px', borderRadius: 20,
              background: dark ? '#2e2e42' : 'var(--gray-50)', border: '1px solid var(--gray-200)',
              color: dark ? '#c0c0d8' : 'var(--gray-600)', fontSize: 12, fontWeight: 500, cursor: 'pointer', lineHeight: 1,
            }}>
            {dark ? (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
            ) : (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
            )}
          </button>
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 32, padding: '40px 24px', maxWidth: 1000, margin: '0 auto', width: '100%', boxSizing: 'border-box', alignItems: 'start' }}>

        {/* Login */}
        <div style={{ background: 'var(--white)', border: '1px solid var(--gray-100)', borderRadius: 'var(--radius-lg)', padding: 28, boxShadow: 'var(--shadow-md)' }}>
          <h2 style={{ fontSize: 20, fontWeight: 600, marginBottom: 4, color: 'var(--gray-900)' }}>Sign in</h2>
          <p style={{ fontSize: 13, color: 'var(--gray-400)', marginBottom: 20 }}>Use the demo credentials to test the system.</p>
          {error && <Alert type="error">{error}</Alert>}
          <form onSubmit={handleSubmit}>
            <Input label="Email" type="email" placeholder="you@school.edu" value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))} />
            <Input label="Password" type="password" placeholder="••••••••" value={form.password} onChange={e => setForm(p => ({ ...p, password: e.target.value }))} />
            <Button type="submit" loading={loading} style={{ width: '100%', justifyContent: 'center' }}>Sign in</Button>
          </form>
        </div>

        {/* Seeded Demo Credentials */}
        <div style={{ background: 'var(--white)', border: '1px solid var(--gray-100)', borderRadius: 'var(--radius-lg)', padding: 24, boxShadow: 'var(--shadow)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 20 }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--red)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--gray-900)' }}>Seeded Demo Credentials</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {DEMO.map(d => (
              <div key={d.role} style={{
                display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px',
                borderRadius: 10, border: '1px solid var(--gray-100)', background: 'var(--off)',
              }}>
                <div style={{
                  width: 40, height: 40, borderRadius: 8, background: d.color,
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                  fontSize: 12, fontWeight: 700, color: '#fff',
                }}>
                  {d.role.split(' ').map(w => w[0]).join('')}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--gray-900)' }}>{d.role}</div>
                  <div style={{ fontSize: 12, color: 'var(--gray-500)', marginBottom: 4, lineHeight: 1.3 }}>{d.desc}</div>
                  <div style={{ fontSize: 12, fontFamily: 'var(--mono)', color: 'var(--gray-400)', wordBreak: 'break-all' }}>
                    {d.email} / {d.pass}
                  </div>
                </div>
                <button onClick={() => fill(d.email, d.pass)} style={{
                  flexShrink: 0, fontSize: 13, fontFamily: 'var(--mono)', background: d.color + '15',
                  color: d.color, border: `1px solid ${d.color}30`, borderRadius: 6, padding: '9px 16px',
                  cursor: 'pointer', fontWeight: 600, whiteSpace: 'nowrap',
                }}>Auto-fill</button>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div style={{ textAlign: 'center', padding: '20px 24px', borderTop: '1px solid var(--gray-100)', fontSize: 12, color: 'var(--gray-400)' }}>
        <Link to="/" style={{ color: 'var(--red)', textDecoration: 'none' }}>← Back to Home</Link> · {settings?.school_name || 'Tech School'} · {settings?.school_address || 'Lagos, Nigeria'}
      </div>
    </div>
  )
}
