import React, { useState, useEffect } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { authApi } from '../../api/client'
import { Input, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [tab, setTab] = useState('student')
  const [cohorts, setCohorts] = useState([])
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '', confirm: '', cohortNumber: '' })
  const [showPw, setShowPw] = useState(false)
  const [showCf, setShowCf] = useState(false)
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
      setError('Please fill in all fields'); return
    }
    if (form.password.length < 6) { setError('Password must be at least 6 characters'); return }
    if (form.password !== form.confirm) { setError('Passwords do not match'); return }
    if (tab === 'student' && !form.cohortNumber.trim()) { setError('Please enter your cohort number'); return }
    setLoading(true)
    try {
      const cohortNumber = form.cohortNumber.match(/\d+/)
      const payload = { name: form.name, email: form.email, phone: form.phone, password: form.password }
      if (tab === 'student') payload.cohortNumber = cohortNumber ? cohortNumber[0] : form.cohortNumber.trim()

      const { data } = tab === 'student'
        ? await authApi.registerStudent(payload)
        : await authApi.registerFacilitator(payload)

      localStorage.setItem('qrs_token', data.token)
      toast.success(`Welcome, ${data.name}! Account created.`)
      const scanToken = localStorage.getItem('qrs_scan_token')
      if (scanToken && data.role === 'STUDENT') {
        localStorage.removeItem('qrs_scan_token')
        navigate('/student/scan?token=' + encodeURIComponent(scanToken))
        return
      }
      if (scanToken) localStorage.removeItem('qrs_scan_token')
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
    <div className="min-h-screen flex items-center justify-center bg-white relative overflow-hidden px-4 py-6">
      <div className="absolute w-[600px] h-[600px] bg-[radial-gradient(circle,rgba(192,57,43,.06)_0%,transparent_70%)] -top-[100px] -right-[100px] pointer-events-none" />

      <div className="w-full max-w-[440px] z-[1]">
        <div className="flex items-center gap-3 mb-8">
          <div className="w-11 h-11 bg-red rounded flex items-center justify-center shrink-0">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/><rect x="18" y="18" width="3" height="3"/><rect x="14" y="18" width="3" height="3"/><rect x="18" y="14" width="3" height="3"/></svg>
          </div>
          <div className="min-w-0">
            <div className="text-base font-semibold truncate">Tech School</div>
            <div className="text-xs text-gray-400 mt-px">Smart Attendance System</div>
          </div>
        </div>

        <h1 className="text-[26px] font-semibold mb-1.5">Create Account</h1>
        <p className="text-[13px] text-gray-400 mb-5">Register to start using the attendance system</p>

        <div className="flex mb-6 border-[1.5px] border-gray-100 rounded overflow-hidden">
          {[{ key: 'student', label: 'Student' }, { key: 'facilitator', label: 'Facilitator' }].map(t => (
            <button key={t.key} onClick={() => { setTab(t.key); setError('') }}
              className={`flex-1 py-5 text-base font-semibold border-0 cursor-pointer transition-all duration-150 ${tab === t.key ? 'bg-red text-white' : 'bg-transparent text-gray-600'}`}>
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
              <label className="block text-[13px] font-medium text-gray-600 mb-1">Cohort</label>
              <select value={form.cohortNumber} onChange={e => update('cohortNumber', e.target.value)}
                className="w-full px-3 py-2.5 text-sm border-[1.5px] border-gray-100 rounded bg-white text-gray-700 outline-none mb-4 appearance-auto">
                <option value="">Select a cohort</option>
                {cohorts.map(c => (
                  <option key={c._id || c.id} value={c.name}>{c.name}</option>
                ))}
              </select>
            </>
          )}
          <div className="relative">
            <Input label="Password" type={showPw ? 'text' : 'password'} placeholder="Min. 6 characters" value={form.password} onChange={e => update('password', e.target.value)} />
            <button type="button" onClick={() => setShowPw(p => !p)} className="absolute right-2.5 top-8 bg-transparent border-0 cursor-pointer p-1 text-gray-400">
              {showPw ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              )}
            </button>
          </div>
          <div className="relative">
            <Input label="Confirm Password" type={showCf ? 'text' : 'password'} placeholder="Re-enter password" value={form.confirm} onChange={e => update('confirm', e.target.value)} />
            <button type="button" onClick={() => setShowCf(p => !p)} className="absolute right-2.5 top-8 bg-transparent border-0 cursor-pointer p-1 text-gray-400">
              {showCf ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              )}
            </button>
          </div>
          <Button type="submit" loading={loading} size="lg" className="w-full justify-center mt-1 !py-[18px] !text-lg">
            Create {tab === 'student' ? 'Student' : 'Facilitator'} Account
          </Button>
        </form>

        <p className="text-center text-[13px] text-gray-400 mt-5">
          Already have an account? <Link to="/" className="text-red font-medium no-underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
