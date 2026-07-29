import React, { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '../../api/client'
import { Input, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [tab, setTab] = useState('student')
  const [cohorts, setCohorts] = useState([])
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '', confirm: '', cohortNumber: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    import('../../api/client').then(({ default: api }) => {
      api.get('/auth/cohorts').then(r => setCohorts(r.data)).catch(() => {})
    })
  }, [])

  const handleSubmit = async e => {
    e.preventDefault()
    setError('')
    if (!form.name || !form.email || !form.phone || !form.password) {
      setError('Please fill in all fields'); return
    }
    if (form.password.length < 6) { setError('Password must be at least 6 characters'); return }
    if (form.password !== form.confirm) { setError('Passwords do not match'); return }
    if (tab === 'student' && !form.cohortNumber.trim()) { setError('Please enter your cohort number'); return }
    setLoading(true)
    try {
      const payload = { name: form.name, email: form.email, phone: form.phone, password: form.password }
      if (tab === 'student') payload.cohortNumber = form.cohortNumber.trim()

      const { data } = tab === 'student'
        ? await authApi.registerStudent(payload)
        : await authApi.registerFacilitator(payload)

      localStorage.setItem('qrs_token', data.token)
      toast.success(`Welcome, ${data.name}! Account created.`)
      const route = data.role === 'STUDENT' ? '/student' : '/facilitator'
      navigate(route)
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  const update = (key, val) => setForm(p => ({ ...p, [key]: val }))

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--white)', position: 'relative', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', width: 600, height: 600, background: 'radial-gradient(circle, rgba(192,57,43,.06) 0%, transparent 70%)', top: -100, right: -100, pointerEvents: 'none' }} />

      <div style={{ width: '100%', maxWidth: 440, padding: '0 24px', zIndex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 32 }}>
          <div style={{ width: 44, height: 44, background: 'var(--red)', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/><rect x="18" y="18" width="3" height="3"/><rect x="14" y="18" width="3" height="3"/><rect x="18" y="14" width="3" height="3"/></svg>
          </div>
          <div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>Tech School</div>
            <div style={{ fontSize: 12, color: 'var(--gray-400)', marginTop: 1 }}>Smart Attendance System</div>
          </div>
        </div>

        <h1 style={{ fontSize: 26, fontWeight: 600, marginBottom: 6 }}>Create Account</h1>
        <p style={{ fontSize: 13, color: 'var(--gray-400)', marginBottom: 20 }}>Register to start using the attendance system</p>

        <div style={{ display: 'flex', gap: 0, marginBottom: 24, border: '1.5px solid var(--gray-100)', borderRadius: 'var(--radius)', overflow: 'hidden' }}>
          {[{ key: 'student', label: 'Student' }, { key: 'facilitator', label: 'Facilitator' }].map(t => (
            <button key={t.key} onClick={() => { setTab(t.key); setError('') }}
              style={{
                flex: 1, padding: '10px 0', fontSize: 13, fontWeight: 500, border: 'none', cursor: 'pointer',
                background: tab === t.key ? 'var(--red)' : 'transparent',
                color: tab === t.key ? 'white' : 'var(--gray-600)',
                transition: 'all .15s'
              }}>
              {t.label}
            </button>
          ))}
        </div>

        {error && <Alert type="error">{error}</Alert>}

        <form onSubmit={handleSubmit}>
          <Input label="Full Name" placeholder="e.g. Ada Okafor" value={form.name} onChange={e => update('name', e.target.value)} />
          <Input label="Email address" type="email" placeholder="you@example.com" value={form.email} onChange={e => update('email', e.target.value)} />
          <Input label="Phone Number" type="tel" placeholder="+234 800 000 0000" value={form.phone} onChange={e => update('phone', e.target.value)} />
          {tab === 'student' && (
            <>
              <Input label="Cohort Number" placeholder="e.g. 29" value={form.cohortNumber} onChange={e => update('cohortNumber', e.target.value)} />
              {cohorts.length > 0 && (
                <p style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: -8, marginBottom: 16 }}>
                  Available cohorts: {cohorts.map(c => {
                    const match = c.name.match(/\d+/)
                    return match ? match[0] : c.name
                  }).join(', ')}
                </p>
              )}
            </>
          )}
          <Input label="Password" type="password" placeholder="Min. 6 characters" value={form.password} onChange={e => update('password', e.target.value)} />
          <Input label="Confirm Password" type="password" placeholder="Re-enter password" value={form.confirm} onChange={e => update('confirm', e.target.value)} />
          <Button type="submit" loading={loading} style={{ width: '100%', justifyContent: 'center', marginTop: 4 }}>
            Create {tab === 'student' ? 'Student' : 'Facilitator'} Account
          </Button>
        </form>

        <p style={{ textAlign: 'center', fontSize: 13, color: 'var(--gray-400)', marginTop: 20 }}>
          Already have an account? <Link to="/login" style={{ color: 'var(--red)', fontWeight: 500, textDecoration: 'none' }}>Sign in</Link>
        </p>
      </div>
    </div>
  )
}
