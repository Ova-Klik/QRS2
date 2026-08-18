import React, { useState, useEffect } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { authApi } from '../../api/client'
import { useTheme } from '../../context/ThemeContext'
import { Input, Select, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { dark, toggle } = useTheme()
  const [cohorts, setCohorts] = useState([])
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '', confirm: '', cohortNumber: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    import('../../api/client').then(({ default: api }) => {
      api.get('/auth/cohorts').then(r => setCohorts(r.data)).catch(() => {})
    })
  }, [])

  useEffect(() => {
    const qrs = searchParams.get('qrs')
    if (qrs) localStorage.setItem('qrs_scan_token', qrs)
  }, [searchParams])

  const handleSubmit = async e => {
    e.preventDefault()
    setError('')
    if (!form.name || !form.email || !form.phone || !form.password) {
      setError('Please fill in all required fields'); return
    }
    if (form.password.length < 6) { setError('Password must be at least 6 characters'); return }
    if (form.password !== form.confirm) { setError('Passwords do not match'); return }
    if (!form.cohortNumber.trim()) { setError('Please select your cohort'); return }
    setLoading(true)
    try {
      const payload = {
        name: form.name,
        email: form.email,
        phone: form.phone,
        password: form.password,
        cohortNumber: form.cohortNumber.trim()
      }

      const { data } = await authApi.registerStudent(payload)

      if (!data.token) {
        toast.success(`Account created! Please check your email to verify before signing in.`)
        navigate('/verify-email?email=' + encodeURIComponent(data.email))
        return
      }

      localStorage.setItem('qrs_token', data.token)
      toast.success(`Welcome, ${data.name}! Student account created.`)
      const scanToken = localStorage.getItem('qrs_scan_token')
      if (scanToken && data.role === 'STUDENT') {
        localStorage.removeItem('qrs_scan_token')
        navigate('/student/scan?token=' + encodeURIComponent(scanToken))
        return
      }
      if (scanToken) localStorage.removeItem('qrs_scan_token')
      navigate('/student')
    } catch (err) {
      setError(err.response?.data?.message || 'Student registration failed')
    } finally {
      setLoading(false)
    }
  }

  const update = (key, val) => setForm(p => ({ ...p, [key]: val }))

  return (
    <div className="min-h-screen flex items-center justify-center bg-off relative overflow-hidden px-4 py-6">
      {/* Theme Toggle */}
      <button onClick={toggle} title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
        className={`absolute top-5 right-5 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-xs font-medium cursor-pointer transition-colors z-10 ${
          dark ? 'bg-[#2e2e42] border-gray-700 text-[#c0c0d8]' : 'bg-gray-50 border-gray-200 text-gray-600'
        }`}>
        {dark ? (
          <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
        ) : (
          <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
        )}
      </button>

      <div className="absolute w-[600px] h-[600px] bg-[radial-gradient(circle,rgba(192,57,43,.06)_0%,transparent_70%)] -top-[100px] -right-[100px] pointer-events-none" />

      <div className="w-full max-w-[440px] z-[1]">
        <div className="flex items-center gap-3 mb-8">
          <div className="w-11 h-11 bg-red rounded flex items-center justify-center shrink-0">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/><rect x="18" y="18" width="3" height="3"/><rect x="14" y="18" width="3" height="3"/><rect x="18" y="14" width="3" height="3"/></svg>
          </div>
          <div className="min-w-0">
            <div className="text-base font-semibold truncate text-gray-900">Tech School</div>
            <div className="text-xs text-gray-400 mt-px">Smart Attendance System</div>
          </div>
        </div>

        <h1 className="text-[26px] font-semibold mb-1.5 text-gray-900">Student Registration</h1>
        <p className="text-[13px] text-gray-400 mb-6">Create a dedicated student account to record attendance</p>

        {error && <Alert type="error">{error}</Alert>}

        <form onSubmit={handleSubmit}>
          <Input label="Full Name" placeholder="e.g. Ada Okafor" value={form.name} onChange={e => update('name', e.target.value)} />
          <Input label="Email address" type="email" placeholder="you@example.com" value={form.email} onChange={e => update('email', e.target.value)} />
          <Input label="Phone Number" type="tel" placeholder="+234 800 000 0000" value={form.phone} onChange={e => update('phone', e.target.value)} />
          <Select label="Cohort" value={form.cohortNumber} onChange={e => update('cohortNumber', e.target.value)}>
            <option value="">Select a cohort</option>
            {cohorts.map(c => (
              <option key={c._id || c.id} value={c.name}>{c.name}</option>
            ))}
          </Select>
          <Input label="Password" type="password" placeholder="Min. 6 characters" value={form.password} onChange={e => update('password', e.target.value)} />
          <Input label="Confirm Password" type="password" placeholder="Re-enter password" value={form.confirm} onChange={e => update('confirm', e.target.value)} />
          <Button type="submit" loading={loading} size="lg" className="w-full justify-center mt-2 !py-[16px] !text-base">
            Create Student Account
          </Button>
        </form>

        <p className="text-center text-[13px] text-gray-400 mt-6">
          Already have an account? <Link to="/" className="text-red font-medium no-underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
