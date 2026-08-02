import React, { useState, useEffect } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useSchool } from '../../context/SchoolContext'
import { useTheme } from '../../context/ThemeContext'
import { Input, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

export default function LandingPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { dark, toggle } = useTheme()
  const { settings } = useSchool()
  const [form, setForm] = useState({ email: '', password: '' })

  useEffect(() => {
    const qrs = searchParams.get('qrs')
    if (qrs) localStorage.setItem('qrs_scan_token', qrs)
  }, [searchParams])
  const [showPw, setShowPw] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

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
      setError(err.response?.data?.message || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-off flex flex-col">
      {/* Top bar */}
      <div className="flex flex-wrap items-center justify-between px-6 py-3 border-b border-gray-100 bg-white">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 bg-red rounded-md flex items-center justify-center">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/></svg>
          </div>
          <span className="text-[15px] font-semibold text-gray-900">QR Attendance</span>
        </div>
        <div className="flex items-center gap-2">
          <Link to="/project" className="text-xs text-gray-500 no-underline px-3 py-1.5 rounded-md border border-gray-200">
            Projection Screen
          </Link>
          <button onClick={toggle} title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            className={dark
              ? 'inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-[#2e2e42] border border-gray-200 text-[#c0c0d8] text-xs font-medium cursor-pointer leading-none'
              : 'inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-gray-50 border border-gray-200 text-gray-600 text-xs font-medium cursor-pointer leading-none'}>
            {dark ? (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
            ) : (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
            )}
          </button>
        </div>
      </div>

      {/* Hero */}
      <div className="flex-1 flex items-center justify-center px-4 py-10">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 lg:gap-12 max-w-[900px] w-full items-center">
          {/* Left — Hero */}
          <div>
            <div className="mb-3">
              <span className="text-[11px] font-semibold text-red bg-red-light px-2.5 py-0.5 rounded-full">v1.0 · Smart Attendance</span>
            </div>
            <h1 className="text-3xl sm:text-[34px] font-bold text-gray-900 leading-tight mb-3">
              {settings?.school_name || 'Tech School'}<br />
              <span className="text-red">QR Attendance System</span>
            </h1>
            <p className="text-sm text-gray-500 leading-relaxed mb-6 max-w-[400px]">
              Secure, contactless attendance tracking with dynamic QR codes, biometric verification, and real-time classroom projection.
            </p>
            <Link to="/project" className="inline-flex items-center gap-2 text-[13px] font-medium text-gray-600 no-underline px-4 py-2.5 rounded border border-gray-200 bg-white">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
              Open Classroom Projection Screen
            </Link>
          </div>

          {/* Right — Login */}
          <div className="bg-white border border-gray-100 rounded-lg p-7 shadow-md">
            <h2 className="text-xl font-black mb-1 text-gray-900">Sign in</h2>
            <p className="text-[13px] text-gray-400 mb-6">Sign in to your account to continue.</p>
            {error && <Alert type="error">{error}</Alert>}
            <form onSubmit={handleSubmit}>
              <Input label="Email" type="email" placeholder="you@school.edu" value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))} />
              <Input label="Password" type="password" placeholder="••••••••" value={form.password} onChange={e => setForm(p => ({ ...p, password: e.target.value }))} />
              <Button type="submit" loading={loading} className="w-full justify-center mb-3">Sign in</Button>
            </form>
            <p className="text-center text-[13px] text-gray-400">
              No account? <Link to="/register" className="text-red font-medium no-underline">Register here</Link>
            </p>
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="text-center px-6 py-5 border-t border-gray-100 text-xs text-gray-400">
        {settings?.school_name || 'Tech School'} · {settings?.school_address || 'Lagos, Nigeria'}
      </div>
    </div>
  )
}
