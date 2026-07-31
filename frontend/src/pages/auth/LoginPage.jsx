import React, { useState, useEffect } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useTheme } from '../../context/ThemeContext'
import { useSchool } from '../../context/SchoolContext'
import { Input, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate  = useNavigate()
  const [searchParams] = useSearchParams()
  const { dark, toggle } = useTheme()
  const { settings } = useSchool()
  const [form, setForm]     = useState({ email: '', password: '' })
  const [showPw, setShowPw] = useState(false)
  const [error, setError]   = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const qrs = searchParams.get('qrs')
    if (qrs) localStorage.setItem('qrs_scan_token', qrs)
  }, [searchParams])

  const handleSubmit = async e => {
    e.preventDefault()
    if (!form.email || !form.password) { setError('Please fill in all fields'); return }
    setError(''); setLoading(true)
    try {
      const data = await login(form.email, form.password)
      toast.success(`Welcome back, ${data.name}!`)
      const scanToken = localStorage.getItem('qrs_scan_token')
      if (scanToken && data.role === 'STUDENT') {
        localStorage.removeItem('qrs_scan_token')
        navigate('/student/scan?token=' + encodeURIComponent(scanToken))
        return
      }
      if (scanToken) localStorage.removeItem('qrs_scan_token')
      const routes = { STUDENT: '/student', FACILITATOR: '/facilitator', SUPER_ADMIN: '/admin' }
      navigate(routes[data.role] || '/')
    } catch (err) {
      const msg = err.response?.data?.message || 'Invalid email or password'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  const fill = (email, password) => setForm({ email, password })

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--white)', position: 'relative', overflow: 'hidden' }}>
      {/* Background accent */}
      <div style={{ position: 'absolute', width: 600, height: 600, background: 'radial-gradient(circle, rgba(192,57,43,.06) 0%, transparent 70%)', top: -100, right: -100, pointerEvents: 'none' }} />

      {/* Theme toggle — top right */}
      <button
        onClick={toggle}
        title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
        style={{
          position: 'absolute', top: 20, right: 24,
          display: 'inline-flex', alignItems: 'center', gap: 5,
          padding: '6px 12px', borderRadius: 20, zIndex: 10,
          background: 'var(--gray-50)', border: '1px solid var(--gray-200)',
          color: 'var(--gray-600)', fontSize: 12, fontWeight: 500,
          cursor: 'pointer', lineHeight: 1,
        }}
      >
        {dark ? (
          <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
        ) : (
          <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
        )}
      </button>

      <div style={{ width: '100%', maxWidth: 400, padding: '0 16px', zIndex: 1 }}>
        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 40 }}>
          <div style={{ width: 44, height: 44, background: 'var(--red)', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/><rect x="18" y="18" width="3" height="3"/><rect x="14" y="18" width="3" height="3"/><rect x="18" y="14" width="3" height="3"/></svg>
          </div>
          <div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>{settings?.school_name || 'Tech School'}</div>
            <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 1 }}>Smart Attendance System</div>
          </div>
        </div>

        <h1 style={{ fontSize: 26, fontWeight: 600, marginBottom: 6 }}>Welcome back</h1>
        <p style={{ fontSize: 13, color: 'var(--gray-400)', marginBottom: 28 }}>Sign in to your account to continue</p>

        {error && <Alert type="error">{error}</Alert>}

        <form onSubmit={handleSubmit}>
          <Input label="Email address" type="email" placeholder="you@techschool.edu" value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))} />
          <div style={{ position: 'relative' }}>
            <Input label="Password" type={showPw ? 'text' : 'password'} placeholder="••••••••" value={form.password} onChange={e => setForm(p => ({ ...p, password: e.target.value }))} />
            <button type="button" onClick={() => setShowPw(p => !p)} style={{ position: 'absolute', right: 10, top: 32, background: 'none', border: 'none', cursor: 'pointer', padding: 4, color: 'var(--gray-400)' }}>
              {showPw ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              )}
            </button>
          </div>
          <Button type="submit" loading={loading} style={{ width: '100%', justifyContent: 'center', marginBottom: 12 }}>
            Sign in
          </Button>
        </form>

        {/* Demo credentials */}
        <div style={{ background: 'var(--off)', border: '1px solid var(--gray-100)', borderRadius: 'var(--radius)', padding: 14, marginTop: 8 }}>
          <p style={{ fontSize: 10, fontWeight: 600, color: 'var(--gray-400)', textTransform: 'uppercase', letterSpacing: '.08em', marginBottom: 10 }}>Demo credentials</p>
          {[
            { label: 'Super Admin',  email: 'admin@techschool.edu',       pass: 'Admin@1234' },
            { label: 'Facilitator',  email: 'james.obi@techschool.edu',   pass: 'Fac@1234' },
            { label: 'Student',      email: 'ada.okafor@techschool.edu',  pass: 'Student@1234' },
          ].map(c => (
            <div key={c.label} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--gray-600)', fontWeight: 500 }}>{c.label}</span>
              <button type="button" onClick={() => fill(c.email, c.pass)} style={{ fontSize: 11, fontFamily: 'var(--mono)', background: 'var(--red-light)', color: 'var(--red)', border: '1px solid var(--red-mid)', borderRadius: 4, padding: '2px 8px', cursor: 'pointer' }}>
                Use
              </button>
            </div>
          ))}
        </div>

        <p style={{ textAlign: 'center', fontSize: 13, color: 'var(--gray-400)', marginTop: 20 }}>
          Don't have an account? <Link to="/register" style={{ color: 'var(--red)', fontWeight: 500, textDecoration: 'none' }}>Register now</Link>
        </p>

        <div style={{ marginTop: 16, textAlign: 'center' }}>
          <Link
            to="/project"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 13,
              fontWeight: 500,
              color: 'var(--gray-700)',
              background: 'var(--off)',
              border: '1px solid var(--gray-200)',
              borderRadius: 'var(--radius)',
              padding: '10px 16px',
              textDecoration: 'none',
              transition: 'all .15s'
            }}
          >
            📺 Open Class Projection Screen
          </Link>
        </div>
      </div>
    </div>
  )
}
