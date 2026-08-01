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
    <div className="min-h-screen bg-off flex flex-col">
      {/* Top bar */}
      <div className="flex flex-wrap items-center justify-between px-6 py-3 border-b border-gray-100 bg-white">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 bg-red rounded-md flex items-center justify-center">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/></svg>
          </div>
          <span className="text-[15px] font-semibold text-gray-900">QR Attendance · Tester Access</span>
        </div>
        <div className="flex items-center gap-2">
          <Link to="/" className="text-xs text-gray-500 no-underline px-3 py-1.5 rounded-md border border-gray-200">
            Home
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

      {/* Content */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-8 px-6 py-10 max-w-[1000px] mx-auto w-full items-start">

        {/* Login */}
        <div className="bg-white border border-gray-100 rounded-lg p-7 shadow-md">
          <h2 className="text-xl font-semibold mb-1 text-gray-900">Sign in</h2>
          <p className="text-[13px] text-gray-400 mb-5">Use the demo credentials to test the system.</p>
          {error && <Alert type="error">{error}</Alert>}
          <form onSubmit={handleSubmit}>
            <Input label="Email" type="email" placeholder="you@school.edu" value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))} />
            <Input label="Password" type="password" placeholder="••••••••" value={form.password} onChange={e => setForm(p => ({ ...p, password: e.target.value }))} />
            <Button type="submit" loading={loading} className="w-full justify-center">Sign in</Button>
          </form>
        </div>

        {/* Seeded Demo Credentials */}
        <div className="bg-white border border-gray-100 rounded-lg p-6 shadow">
          <div className="flex items-center gap-2 mb-5">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--red)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            <span className="text-[15px] font-semibold text-gray-900">Seeded Demo Credentials</span>
          </div>
          <div className="flex flex-col gap-3">
            {DEMO.map(d => (
              <div key={d.role} className="flex items-center gap-3 px-4 py-3.5 rounded border border-gray-100 bg-off">
                <div className="w-10 h-10 rounded-md flex items-center justify-center shrink-0 text-xs font-bold text-white" style={{ background: d.color }}>
                  {d.role.split(' ').map(w => w[0]).join('')}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-gray-900">{d.role}</div>
                  <div className="text-xs text-gray-500 mb-1 leading-tight">{d.desc}</div>
                  <div className="text-xs font-mono text-gray-400 break-all">
                    {d.email} / {d.pass}
                  </div>
                </div>
                <button onClick={() => fill(d.email, d.pass)} className="shrink-0 text-[13px] font-mono rounded-md px-4 py-2 cursor-pointer font-semibold whitespace-nowrap" style={{ background: d.color + '15', color: d.color, border: `1px solid ${d.color}30` }}>Auto-fill</button>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="text-center px-6 py-5 border-t border-gray-100 text-xs text-gray-400">
        <Link to="/" className="text-red no-underline">← Back to Home</Link> · {settings?.school_name || 'Tech School'} · {settings?.school_address || 'Lagos, Nigeria'}
      </div>
    </div>
  )
}
