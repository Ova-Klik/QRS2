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
    <div className="min-h-screen flex items-center justify-center bg-white relative overflow-hidden px-4">
      {/* Background accent */}
      <div className="absolute w-[600px] h-[600px] bg-[radial-gradient(circle,rgba(192,57,43,.06)_0%,transparent_70%)] -top-[100px] -right-[100px] pointer-events-none" />

      {/* Theme toggle — top right */}
      <button
        onClick={toggle}
        title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
        className="absolute top-5 right-4 sm:right-6 inline-flex items-center gap-[5px] px-3 py-1.5 rounded-full z-[10] bg-gray-50 border border-gray-200 text-gray-600 text-xs font-medium cursor-pointer leading-none"
      >
        {dark ? (
          <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
        ) : (
          <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
        )}
      </button>

      <div className="w-full max-w-[400px] z-[1]">
        {/* Logo */}
        <div className="flex items-center gap-3 mb-10">
          <div className="w-11 h-11 bg-red rounded flex items-center justify-center shrink-0">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/><rect x="18" y="18" width="3" height="3"/><rect x="14" y="18" width="3" height="3"/><rect x="18" y="14" width="3" height="3"/></svg>
          </div>
          <div className="min-w-0">
            <div className="text-base font-semibold truncate">{settings?.school_name || 'Tech School'}</div>
            <div className="text-xs text-gray-400 mt-px">Smart Attendance System</div>
          </div>
        </div>

        <h1 className="text-[26px] font-semibold mb-1.5">Welcome back</h1>
        <p className="text-[13px] text-gray-400 mb-7">Sign in to your account to continue</p>

        {error && <Alert type="error">{error}</Alert>}

        <form onSubmit={handleSubmit}>
          <Input label="Email address" type="email" placeholder="you@techschool.edu" value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))} />
          <div className="relative">
            <Input label="Password" type={showPw ? 'text' : 'password'} placeholder="••••••••" value={form.password} onChange={e => setForm(p => ({ ...p, password: e.target.value }))} />
            <button type="button" onClick={() => setShowPw(p => !p)} className="absolute right-2.5 top-8 bg-transparent border-0 cursor-pointer p-1 text-gray-400">
              {showPw ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              )}
            </button>
          </div>
          <Button type="submit" loading={loading} className="w-full justify-center mb-3">
            Sign in
          </Button>
        </form>

        {/* Demo credentials */}
        <div className="bg-off border border-gray-100 rounded p-3.5 mt-2">
          <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2.5">Demo credentials</p>
          {[
            { label: 'Super Admin',  email: 'admin@techschool.edu',       pass: 'Admin@1234' },
            { label: 'Facilitator',  email: 'james.obi@techschool.edu',   pass: 'Fac@1234' },
            { label: 'Student',      email: 'ada.okafor@techschool.edu',  pass: 'Student@1234' },
          ].map(c => (
            <div key={c.label} className="flex items-center justify-between mb-1.5">
              <span className="text-xs text-gray-600 font-medium min-w-0 truncate">{c.label}</span>
              <button type="button" onClick={() => fill(c.email, c.pass)} className="text-[11px] font-mono bg-red-light text-red border border-red-mid rounded-[4px] px-2 py-0.5 cursor-pointer shrink-0">
                Use
              </button>
            </div>
          ))}
        </div>

        <p className="text-center text-[13px] text-gray-400 mt-5">
          Don't have an account? <Link to="/register" className="text-red font-medium no-underline">Register now</Link>
        </p>

        <div className="mt-4 text-center">
          <Link
            to="/project"
            className="inline-flex items-center gap-1.5 text-[13px] font-medium text-gray-700 bg-off border border-gray-200 rounded px-4 py-2.5 no-underline transition-all duration-150 hover:border-gray-400"
          >
            📺 Open Class Projection Screen
          </Link>
        </div>
      </div>
    </div>
  )
}
