import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useSchool } from '../../context/SchoolContext'
import { useTheme } from '../../context/ThemeContext'
import { Input, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

export default function LandingPage() {
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
          <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--gray-900)' }}>QR Attendance</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Link to="/project" style={{ fontSize: 12, color: 'var(--gray-500)', textDecoration: 'none', padding: '6px 12px', borderRadius: 6, border: '1px solid var(--gray-200)' }}>
            Projection Screen
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

      {/* Hero */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '40px 24px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 48, maxWidth: 900, width: '100%', alignItems: 'center' }}>
          {/* Left — Hero */}
          <div>
            <div style={{ marginBottom: 12 }}>
              <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--red)', background: 'var(--red-light)', padding: '3px 10px', borderRadius: 20 }}>v1.0 · Smart Attendance</span>
            </div>
            <h1 style={{ fontSize: 34, fontWeight: 700, color: 'var(--gray-900)', lineHeight: 1.2, marginBottom: 12 }}>
              {settings?.school_name || 'Tech School'}<br />
              <span style={{ color: 'var(--red)' }}>QR Attendance System</span>
            </h1>
            <p style={{ fontSize: 14, color: 'var(--gray-500)', lineHeight: 1.6, marginBottom: 24, maxWidth: 400 }}>
              Secure, contactless attendance tracking with dynamic QR codes, biometric verification, and real-time classroom projection.
            </p>
            <Link to="/project" style={{
              display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 500,
              color: 'var(--gray-600)', textDecoration: 'none', padding: '10px 16px', borderRadius: 8,
              border: '1px solid var(--gray-200)', background: 'var(--white)',
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
              Open Classroom Projection Screen
            </Link>
          </div>

          {/* Right — Login */}
          <div style={{ background: 'var(--white)', border: '1px solid var(--gray-100)', borderRadius: 'var(--radius-lg)', padding: 28, boxShadow: 'var(--shadow-md)' }}>
            <h2 style={{ fontSize: 20, fontWeight: 600, marginBottom: 4, color: 'var(--gray-900)' }}>Sign in</h2>
            <p style={{ fontSize: 13, color: 'var(--gray-400)', marginBottom: 24 }}>Sign in to your account to continue.</p>
            {error && <Alert type="error">{error}</Alert>}
            <form onSubmit={handleSubmit}>
              <Input label="Email" type="email" placeholder="you@school.edu" value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))} />
              <Input label="Password" type="password" placeholder="••••••••" value={form.password} onChange={e => setForm(p => ({ ...p, password: e.target.value }))} />
              <Button type="submit" loading={loading} style={{ width: '100%', justifyContent: 'center', marginBottom: 12 }}>Sign in</Button>
            </form>
            <p style={{ textAlign: 'center', fontSize: 13, color: 'var(--gray-400)' }}>
              No account? <Link to="/register" style={{ color: 'var(--red)', fontWeight: 500, textDecoration: 'none' }}>Register here</Link>
            </p>
          </div>
        </div>
      </div>

      {/* Footer */}
      <div style={{ textAlign: 'center', padding: '20px 24px', borderTop: '1px solid var(--gray-100)', fontSize: 12, color: 'var(--gray-400)' }}>
        {settings?.school_name || 'Tech School'} · {settings?.school_address || 'Lagos, Nigeria'}
      </div>
    </div>
  )
}
