/**
 * Unified Settings Pages for all roles.
 * Exports: StudentSettings, FacilitatorSettings, AdminSettings
 *
 * Student  → Profile · Trusted Devices (WebAuthn + manual fingerprint) · Notifications · Appearance
 * Facilitator → Profile · Session Defaults · Notifications · Security
 * Admin    → School Profile · Attendance Policy · Network/Geofence · Data Export · Security
 */

import React, { useState, useEffect, useCallback } from 'react'
import {
  Card, PageHeader, Button, Input, Alert, LoadingPage
} from '../../components/common/UI'
import { useAuth } from '../../context/AuthContext'
import { useTheme } from '../../context/ThemeContext'
import { useSchool } from '../../context/SchoolContext'
import {
  isPlatformAuthenticatorAvailable,
  registerBiometric,
} from '../../utils/webauthn'
import { authApi, adminApi, facilitatorApi, studentApi } from '../../api/client'
import toast from 'react-hot-toast'
import { format } from 'date-fns'

const ICONS = {
  user: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>,
  clock: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>,
  bell: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>,
  lock: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>,
  sun: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>,
  school: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M22 9 12 5 2 9l10 4 10-4z"/><path d="M6 10v7a4 4 0 0 0 4 4h4a4 4 0 0 0 4-4v-7"/><polyline points="6 11 6 7 12 3 18 7 18 11"/></svg>,
  policy: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>,
  globe: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>,
  download: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>,
  settings: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>,
  server: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>,
  save: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>,
  key: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.78 7.78 5.5 5.5 0 0 1 7.78-7.78zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>,
  phone: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>,
  finger: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2a4 4 0 0 1 4 4v7a3 3 0 0 1-6 0V6a1 1 0 0 1 2 0v7"/><path d="M8 12V6a2 2 0 0 1 4 0v6"/><path d="M16 10V6a2 2 0 0 0-2-2"/><path d="M12 22a8 8 0 0 0 8-8"/></svg>,
}

// ────────────────────────────────────────────────────────────────
// Shared primitives
// ────────────────────────────────────────────────────────────────

/** Pill-style tab bar */
function Tabs({ tabs, active, onChange }) {
  return (
    <div className="flex flex-wrap gap-1 p-1.5 bg-gray-50 rounded-xl mb-6 border border-gray-100">
      {tabs.map(t => (
        <button
          key={t.id}
          onClick={() => onChange(t.id)}
          className={`inline-flex items-center gap-1.5 px-4 py-[7px] rounded text-[13px] cursor-pointer transition-all ${active === t.id ? 'bg-white text-gray-900 border border-gray-200 shadow' : 'bg-transparent text-gray-500 border border-transparent'}`}
        >
          <span>{t.icon}</span> {t.label}
        </button>
      ))}
    </div>
  )
}

/** Section divider with title */
function Section({ title, children, last }) {
  return (
    <div className={last ? '' : 'mb-5'}>
      <div className="text-[11px] font-bold tracking-wider uppercase text-gray-400 mb-3 pb-2 border-b border-gray-100">
        {title}
      </div>
      {children}
    </div>
  )
}

/** Toggle switch */
function Toggle({ checked, onChange, label, sublabel }) {
  return (
    <label className="flex items-start gap-3 cursor-pointer mb-3.5">
      <div
        onClick={() => onChange(!checked)}
        className={`w-10 h-[22px] rounded-[11px] shrink-0 mt-0.5 relative cursor-pointer transition-colors ${checked ? 'bg-red' : 'bg-gray-200'}`}
      >
        <div className="w-[18px] h-[18px] rounded-full bg-white absolute top-0.5 transition-all duration-200 shadow-[0_1px_3px_rgba(0,0,0,0.2)]" style={{ left: checked ? 20 : 2 }} />
      </div>
      <div>
        <div className="text-[13px] font-medium text-gray-900">{label}</div>
        {sublabel && <div className="text-xs text-gray-400 mt-0.5">{sublabel}</div>}
      </div>
    </label>
  )
}

/** Profile avatar with initials */
function Avatar({ name, size = 72 }) {
  const initials = (name || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase()
  const colors = ['#C0392B', '#8E44AD', '#2980B9', '#27AE60', '#D35400']
  const color = colors[(name || '').length % colors.length]
  return (
    <div className="rounded-full flex items-center justify-center text-white font-bold shrink-0 shadow-[0_4px_12px_rgba(0,0,0,0.15)]" style={{ width: size, height: size, background: color, fontSize: size * 0.32 }}>
      {initials}
    </div>
  )
}

/** Device fingerprint generator from browser properties */
function getBrowserFingerprint() {
  const data = [
    navigator.userAgent,
    navigator.language,
    screen.width + 'x' + screen.height,
    screen.colorDepth,
    new Date().getTimezoneOffset(),
    navigator.hardwareConcurrency,
    navigator.platform,
  ].join('|')
  let hash = 0
  for (let i = 0; i < data.length; i++) {
    const chr = data.charCodeAt(i)
    hash = ((hash << 5) - hash) + chr
    hash |= 0
  }
  return Math.abs(hash).toString(16).padStart(8, '0').toUpperCase()
}

// ────────────────────────────────────────────────────────────────
// Password change (shared across all roles)
// ────────────────────────────────────────────────────────────────
function PasswordSection() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [loading, setLoading] = useState(false)

  const handleSubmit = async e => {
    e.preventDefault()
    if (form.newPassword.length < 6) { toast.error('Password must be at least 6 characters'); return }
    if (form.newPassword !== form.confirm) { toast.error('Passwords do not match'); return }
    setLoading(true)
    try {
      await authApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      toast.success('Password updated successfully')
      setForm({ currentPassword: '', newPassword: '', confirm: '' })
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to change password')
    } finally { setLoading(false) }
  }

  return (
    <Section title="Password">
      <form onSubmit={handleSubmit}>
        <Input label="Current password" type="password" value={form.currentPassword}
          onChange={e => setForm(p => ({ ...p, currentPassword: e.target.value }))} />
        <Input label="New password (min 6 chars)" type="password" value={form.newPassword}
          onChange={e => setForm(p => ({ ...p, newPassword: e.target.value }))} />
        <Input label="Confirm new password" type="password" value={form.confirm}
          onChange={e => setForm(p => ({ ...p, confirm: e.target.value }))} />
        <Button type="submit" loading={loading}>{ICONS.key} Update Password</Button>
      </form>
    </Section>
  )
}

// ────────────────────────────────────────────────────────────────
// STUDENT SETTINGS
// ────────────────────────────────────────────────────────────────
export function StudentSettings() {
  const { user } = useAuth()
  const { dark, toggle } = useTheme()
  const [tab, setTab] = useState('profile')
  const [platformAuth, setPlatformAuth] = useState(false)
  const [biometricStatus, setBiometricStatus] = useState('idle') // idle | loading | registered | failed
  const [devices, setDevices] = useState([])
  const [devicesLoading, setDevicesLoading] = useState(true)
  const [manualName, setManualName] = useState('')
  const [manualLoading, setManualLoading] = useState(false)
  const [notifs, setNotifs] = useState({
    absenceAlert: true,
    excuseUpdate: true,
    sessionStart: false,
    weeklyReport: true,
  })

  useEffect(() => {
    isPlatformAuthenticatorAvailable().then(setPlatformAuth).catch(() => {})
    loadDevices()
  }, [])

  const loadDevices = () => {
    setDevicesLoading(true)
    studentApi.dashboard().then(r => {
      const d = r.data?.deviceStatus
      if (d?.registered) {
        setDevices([{ id: 'primary', name: 'Registered Device', fingerprint: d.fingerprint, registered: true, primary: true }])
        setBiometricStatus('registered')
      } else {
        setDevices([])
      }
    }).catch(() => {}).finally(() => setDevicesLoading(false))
  }

  // WebAuthn biometric registration
  const handleBiometricRegister = async () => {
    setBiometricStatus('loading')
    try {
      toast.loading('Waiting for fingerprint sensor...', { id: 'bio' })
      const reg = await registerBiometric(user?.userId || user?.id || 'student')
      await authApi.webauthnRegister({ credentialId: reg.credentialId, publicKey: reg.publicKey })
      setBiometricStatus('registered')
      toast.success('Fingerprint registered!', { id: 'bio' })
      loadDevices()
    } catch (err) {
      setBiometricStatus('idle')
      toast.error(err.message?.includes('cancel') ? 'Cancelled' : 'Biometric registration failed', { id: 'bio' })
    }
  }

  // Manual device fingerprint registration
  const handleManualRegister = async () => {
    if (!manualName.trim()) { toast.error('Enter a device name'); return }
    setManualLoading(true)
    try {
      const fingerprint = getBrowserFingerprint()
      await studentApi.registerDevice({ deviceName: manualName.trim(), fingerprint })
      toast.success(`Device "${manualName}" registered!`)
      setManualName('')
      loadDevices()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to register device')
    } finally { setManualLoading(false) }
  }

  const tabs = [
    { id: 'profile',  icon: ICONS.user, label: 'Profile'  },
    { id: 'devices',  icon: ICONS.phone, label: 'Devices'  },
    { id: 'notifs',   icon: ICONS.bell, label: 'Notifications' },
    { id: 'security', icon: ICONS.lock, label: 'Security' },
    { id: 'appearance', icon: ICONS.sun, label: 'Appearance' },
  ]

  return (
    <>
      <PageHeader title="Settings" subtitle="Manage your account and preferences" />
      <div className="p-4 sm:p-6 max-w-[640px] animate-fade-in">
        <Tabs tabs={tabs} active={tab} onChange={setTab} />

        {/* ── Profile ─────────────────────────────── */}
        {tab === 'profile' && (
          <Card>
            <div className="flex flex-wrap items-center gap-5 mb-6">
              <Avatar name={user?.name} size={72} />
              <div className="min-w-0">
                <div className="text-xl font-bold">{user?.name}</div>
                <div className="text-[13px] text-gray-400 font-mono">{user?.email}</div>
                <span className="inline-block mt-1.5 px-2.5 py-[3px] rounded-full bg-red-light text-red text-[11px] font-semibold border border-red-mid">
                  Student
                </span>
              </div>
            </div>
            <Section title="Account Info">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {[
                  { label: 'Full Name', value: user?.name },
                  { label: 'Email Address', value: user?.email },
                  { label: 'User ID', value: user?.userId || user?.id },
                  { label: 'Role', value: 'Student' },
                ].map(({ label, value }) => (
                  <div key={label} className="px-3.5 py-3 bg-gray-50 rounded border border-gray-100">
                    <div className="text-[10px] font-semibold tracking-wide uppercase text-gray-400 mb-1">{label}</div>
                    <div className={`text-[13px] font-medium ${label === 'User ID' || label === 'Email Address' ? 'font-mono' : ''}`}>{value || '—'}</div>
                  </div>
                ))}
              </div>
            </Section>
          </Card>
        )}

        {/* ── Devices ─────────────────────────────── */}
        {tab === 'devices' && (
          <div className="flex flex-col gap-4">

            {/* WebAuthn Biometric */}
            <Card>
              <Section title="Biometric Fingerprint Login">
                <div className="flex items-start gap-4 mb-4">
                  <div className="w-14 h-14 rounded-xl shrink-0 flex items-center justify-center"
                    style={{
                      background: biometricStatus === 'registered' ? 'var(--green-light)' : 'var(--gray-50)',
                      border: `2px solid ${biometricStatus === 'registered' ? '#a8dbb8' : 'var(--gray-200)'}`,
                    }}>
                    {biometricStatus === 'registered' ? (
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--green-dark)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                    ) : (
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--gray-400)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2a4 4 0 0 1 4 4v7a3 3 0 0 1-6 0V6a1 1 0 0 1 2 0v7"/><path d="M8 12V6a2 2 0 0 1 4 0v6"/><path d="M16 10V6a2 2 0 0 0-2-2"/><path d="M12 22a8 8 0 0 0 8-8"/></svg>
                    )}
                  </div>
                  <div className="min-w-0">
                    <div className="text-sm font-semibold mb-1">
                      {biometricStatus === 'registered' ? 'Fingerprint Registered' : 'Register Fingerprint'}
                    </div>
                    <p className="text-xs text-gray-400 leading-relaxed">
                      Use your device's built-in fingerprint sensor or Face ID to log in instantly — no password needed.
                      Works with Windows Hello, Touch ID, and Android fingerprint readers.
                    </p>
                  </div>
                </div>

                {platformAuth ? (
                  biometricStatus === 'registered' ? (
                    <Alert type="success">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: 6, verticalAlign: 'middle' }}><polyline points="20 6 9 17 4 12"/></svg>
                      Fingerprint registered on this device. You can now use it when logging in.
                    </Alert>
                  ) : (
                    <Button
                      loading={biometricStatus === 'loading'}
                      onClick={handleBiometricRegister}
                    >
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2a4 4 0 0 1 4 4v7a3 3 0 0 1-6 0V6a1 1 0 0 1 2 0v7"/><path d="M8 12V6a2 2 0 0 1 4 0v6"/><path d="M16 10V6a2 2 0 0 0-2-2"/><path d="M12 22a8 8 0 0 0 8-8"/></svg>
                      Register Fingerprint / Face ID
                    </Button>
                  )
                ) : (
                  <Alert type="warning">
                    Your browser or device does not support biometric authentication (WebAuthn). Try Chrome, Edge, or Safari on a device with a fingerprint sensor.
                  </Alert>
                )}
              </Section>
            </Card>

            {/* Manual Device Registration */}
            <Card>
              <Section title="Manual Device Registration">
                <p className="text-xs text-gray-400 mb-4 leading-relaxed">
                  Register this browser/device manually using its unique fingerprint. Once registered, the system will recognise it for attendance marking — even without biometrics.
                </p>

                <div className="px-4 py-3 rounded mb-4 bg-gray-50 border border-gray-100 font-mono text-xs">
                  <div className="text-[10px] font-semibold uppercase text-gray-400 mb-1">This Device Fingerprint</div>
                  <div className="text-gray-700 tracking-[.08em]">{getBrowserFingerprint()}</div>
                  <div className="text-[10px] text-gray-400 mt-1">
                    {navigator.platform} · {screen.width}×{screen.height} · {navigator.language}
                  </div>
                </div>

                <div className="flex gap-2.5 flex-wrap">
                  <Input
                    label="Device Name"
                    placeholder="e.g. My Phone, Lab PC 3"
                    value={manualName}
                    onChange={e => setManualName(e.target.value)}
                    className="mb-0 flex-1"
                  />
                  <Button
                    loading={manualLoading}
                    onClick={handleManualRegister}
                    className="mt-[22px] shrink-0"
                  >
                    Register
                  </Button>
                </div>
              </Section>

              {/* Registered devices list */}
              {!devicesLoading && devices.length > 0 && (
                <Section title="Registered Devices">
                  {devices.map(d => (
                    <div key={d.id} className="flex items-center gap-3 px-3.5 py-2.5 bg-gray-50 rounded mb-2 border border-gray-100">
                      <div className="text-2xl">{d.primary ? (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                      ) : (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
                      )}</div>
                      <div className="flex-1 min-w-0">
                        <div className="text-[13px] font-medium">{d.name}</div>
                        <div className="text-[11px] font-mono text-gray-400">{d.fingerprint}</div>
                      </div>
                      <span className="px-2.5 py-[3px] rounded-full text-[10px] font-semibold bg-green-light text-green-dark border border-[#a8dbb8]">Trusted</span>
                    </div>
                  ))}
                </Section>
              )}
            </Card>

            {/* Quick tip */}
            <Card className="bg-[linear-gradient(135deg,var(--red-light),var(--blue-light))]">
              <div className="flex gap-3 items-start">
                <svg className="shrink-0" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--gray-500)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5.71.71 1.23 1.52 1.41 2.5"/></svg>
                <div className="min-w-0">
                  <div className="font-semibold text-[13px] mb-1">Why register your device?</div>
                  <p className="text-xs text-gray-600 leading-relaxed">
                    Registering your device allows the system to verify you are physically present on campus
                    without requiring a password each time. Combined with the QR code scan, it provides
                    a two-factor attendance confirmation.
                  </p>
                </div>
              </div>
            </Card>
          </div>
        )}

        {/* ── Notifications ───────────────────────── */}
        {tab === 'notifs' && (
          <Card>
            <Section title="Attendance Alerts">
              <Toggle
                checked={notifs.absenceAlert}
                onChange={v => setNotifs(p => ({ ...p, absenceAlert: v }))}
                label="Absence Warning"
                sublabel="Get alerted when you are marked absent after the session ends"
              />
              <Toggle
                checked={notifs.sessionStart}
                onChange={v => setNotifs(p => ({ ...p, sessionStart: v }))}
                label="Session Start Reminder"
                sublabel="Remind you 10 minutes before the attendance window opens"
              />
            </Section>
            <Section title="Excuse Requests">
              <Toggle
                checked={notifs.excuseUpdate}
                onChange={v => setNotifs(p => ({ ...p, excuseUpdate: v }))}
                label="Excuse Status Updates"
                sublabel="Notify you when a facilitator approves or rejects your request"
              />
            </Section>
            <Section title="Reports" last>
              <Toggle
                checked={notifs.weeklyReport}
                onChange={v => setNotifs(p => ({ ...p, weeklyReport: v }))}
                label="Weekly Attendance Summary"
                sublabel="Receive a weekly summary of your attendance record every Friday"
              />
            </Section>
            <Button onClick={() => toast.success('Notification preferences saved')}>
              {ICONS.save} Save Preferences
            </Button>
          </Card>
        )}

        {/* ── Security ────────────────────────────── */}
        {tab === 'security' && (
          <Card>
            <PasswordSection />
            <div className="h-px bg-gray-100 my-5" />
            <Section title="Session & Security" last>
              <div className="px-4 py-3 bg-gray-50 rounded border border-gray-100 mb-3">
                <div className="text-xs font-semibold text-gray-400 mb-1">LAST LOGIN</div>
                <div className="text-[13px]">{format(new Date(), 'dd MMM yyyy, HH:mm')}</div>
              </div>
              <Alert type="info">
                For your security, sessions expire automatically after inactivity. Always log out on shared devices.
              </Alert>
            </Section>
          </Card>
        )}

        {/* ── Appearance ──────────────────────────── */}
        {tab === 'appearance' && (
          <Card>
            <Section title="Theme">
              <div className="flex flex-wrap gap-3 mb-5">
                {[
                  { id: 'light', label: 'Light', sublabel: 'Clean & bright' },
                  { id: 'dark',  label: 'Dark',  sublabel: 'Easy on eyes' },
                ].map(t => (
                  <button
                    key={t.id}
                    onClick={() => { if ((t.id === 'dark') !== dark) toggle() }}
                    className={`flex-1 min-w-[120px] px-3 py-4 rounded-xl cursor-pointer text-center ${(t.id === 'dark') === dark ? 'border-2 border-red bg-red-light' : 'border-2 border-gray-200 bg-gray-50'}`}
                  >
                    <div className={`mb-1.5 ${(t.id === 'dark') === dark ? 'text-red' : 'text-gray-400'}`}>
                      {t.id === 'light' ? (
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
                      ) : (
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
                      )}
                    </div>
                    <div className={`text-[13px] font-semibold ${(t.id === 'dark') === dark ? 'text-red' : 'text-gray-700'}`}>{t.label}</div>
                    <div className="text-[11px] text-gray-400 mt-0.5">{t.sublabel}</div>
                  </button>
                ))}
              </div>
            </Section>
            <Section title="System" last>
              <div className="px-4 py-3 bg-gray-50 rounded border border-gray-100">
                <div className="text-xs font-semibold text-gray-400 mb-2">BROWSER INFO</div>
                <div className="text-xs font-mono text-gray-600 leading-relaxed">
                  <div>{navigator.platform}</div>
                  <div>{navigator.language} · {screen.width}×{screen.height}</div>
                  <div>{navigator.userAgent.split(' ').slice(-2).join(' ')}</div>
                </div>
              </div>
            </Section>
          </Card>
        )}
      </div>
    </>
  )
}

// ────────────────────────────────────────────────────────────────
// FACILITATOR SETTINGS
// ────────────────────────────────────────────────────────────────
export function FacilitatorSettings() {
  const { user } = useAuth()
  const { dark, toggle } = useTheme()
  const { settings: schoolSettings, updateSchoolSettings } = useSchool()
  const [tab, setTab] = useState('profile')
  const [facNetSettings, setFacNetSettings] = useState(null)
  const [facNetLoading, setFacNetLoading] = useState(true)
  const [facNetSaving, setFacNetSaving] = useState(false)
  const [sessionDefaults, setSessionDefaults] = useState({
    defaultDuration: '60',
    autoExpire: true,
    requireManualApproval: false,
    lateGraceMinutes: '30',
  })
  const [notifs, setNotifs] = useState({
    excuseAlert: true,
    absentAlert: false,
    sessionEnd: true,
    dailyReport: true,
  })
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    facilitatorApi.getNetworkSettings()
      .then(r => setFacNetSettings(r.data))
      .catch(() => {})
      .finally(() => setFacNetLoading(false))
  }, [])

  const updateFacNet = (key, value) => setFacNetSettings(prev => ({ ...prev, [key]: value }))

  const saveFacNetwork = async () => {
    setFacNetSaving(true)
    try {
      await facilitatorApi.updateNetworkSettings(facNetSettings)
      updateSchoolSettings(facNetSettings)
      toast.success('Attendance time settings saved')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save')
    } finally {
      setFacNetSaving(false)
    }
  }

  const tabs = [
    { id: 'profile',   icon: ICONS.user, label: 'Profile' },
    { id: 'sessions',  icon: ICONS.clock, label: 'Session Defaults' },
    { id: 'policy',    icon: ICONS.policy, label: 'Attendance Policy' },
    { id: 'notifs',    icon: ICONS.bell, label: 'Notifications' },
    { id: 'security',  icon: ICONS.lock, label: 'Security' },
    { id: 'appearance', icon: ICONS.sun, label: 'Appearance' },
  ]

  const saveSession = () => {
    setSaving(true)
    setTimeout(() => {
      toast.success('Session defaults saved')
      setSaving(false)
    }, 600)
  }

  return (
    <>
      <PageHeader title="Settings" subtitle="Facilitator preferences & configuration" />
      <div className="p-4 sm:p-6 max-w-[640px] animate-fade-in">
        <Tabs tabs={tabs} active={tab} onChange={setTab} />

        {/* ── Profile ─────────────────────────────── */}
        {tab === 'profile' && (
          <Card>
            <div className="flex flex-wrap items-center gap-5 mb-6">
              <Avatar name={user?.name} size={72} />
              <div className="min-w-0">
                <div className="text-xl font-bold">{user?.name}</div>
                <div className="text-[13px] text-gray-400 font-mono">{user?.email}</div>
                <span className="inline-block mt-1.5 px-2.5 py-[3px] rounded-full bg-blue-light text-blue-dark text-[11px] font-semibold border border-[#bfdbfe]">
                  Facilitator
                </span>
              </div>
            </div>
            <Section title="Account Info" last>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {[
                  { label: 'Full Name', value: user?.name },
                  { label: 'Email Address', value: user?.email },
                  { label: 'User ID', value: user?.userId || user?.id },
                  { label: 'Role', value: 'Facilitator' },
                ].map(({ label, value }) => (
                  <div key={label} className="px-3.5 py-3 bg-gray-50 rounded border border-gray-100">
                    <div className="text-[10px] font-semibold tracking-wide uppercase text-gray-400 mb-1">{label}</div>
                    <div className={`text-[13px] font-medium ${label === 'User ID' || label === 'Email Address' ? 'font-mono' : ''}`}>{value || '—'}</div>
                  </div>
                ))}
              </div>
            </Section>
          </Card>
        )}

        {/* ── Session Defaults ────────────────────── */}
        {tab === 'sessions' && (
          <Card>
            <Section title="QR Session Configuration">
              <p className="text-xs text-gray-400 mb-4 leading-relaxed">
                These defaults are pre-filled when you generate a QR session. You can still override them per session.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
                <div>
                  <label className="text-xs font-medium text-gray-600 block mb-1.5">
                    Default Session Duration (minutes)
                  </label>
                  <input
                    type="number" min="5" max="180"
                    value={sessionDefaults.defaultDuration}
                    onChange={e => setSessionDefaults(p => ({ ...p, defaultDuration: e.target.value }))}
                    className="w-full px-3 py-2.5 rounded border border-gray-200 text-sm bg-white text-gray-900"
                  />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 block mb-1.5">
                    Late Grace Period (minutes after 8:30)
                  </label>
                  <input
                    type="number" min="0" max="60"
                    value={sessionDefaults.lateGraceMinutes}
                    onChange={e => setSessionDefaults(p => ({ ...p, lateGraceMinutes: e.target.value }))}
                    className="w-full px-3 py-2.5 rounded border border-gray-200 text-sm bg-white text-gray-900"
                  />
                </div>
              </div>
              <Toggle
                checked={sessionDefaults.autoExpire}
                onChange={v => setSessionDefaults(p => ({ ...p, autoExpire: v }))}
                label="Auto-expire session when duration ends"
                sublabel="QR code becomes invalid automatically when the countdown reaches zero"
              />
              <Toggle
                checked={sessionDefaults.requireManualApproval}
                onChange={v => setSessionDefaults(p => ({ ...p, requireManualApproval: v }))}
                label="Flag late arrivals for manual review"
                sublabel="Late scans will appear in your review queue before being confirmed"
              />
            </Section>
            <Button loading={saving} onClick={saveSession}>{ICONS.save} Save Session Defaults</Button>
          </Card>
        )}

        {/* ── Attendance Policy ───────────────────── */}
        {tab === 'policy' && (
          <Card>
            <Section title="Attendance Time Windows">
              <p className="text-xs text-gray-400 mb-4 leading-relaxed">
                Define when the QR attendance session is open, and when a student is marked late. Changes affect the timer countdown immediately.
              </p>
              {facNetLoading ? (
                <div className="text-center p-5"><div className="inline-block h-5 w-5 rounded-full border-2 border-gray-200 border-t-red animate-spin" /></div>
              ) : facNetSettings ? (
                <>
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-4">
                    <Input
                      label="Session Opens (HH:MM)"
                      value={facNetSettings.qr_window_start || ''}
                      onChange={e => updateFacNet('qr_window_start', e.target.value)}
                      placeholder="07:00"
                    />
                    <Input
                      label="Session Closes (HH:MM)"
                      value={facNetSettings.qr_window_end || ''}
                      onChange={e => updateFacNet('qr_window_end', e.target.value)}
                      placeholder="12:00"
                    />
                    <Input
                      label="Late After (HH:MM)"
                      value={facNetSettings.late_threshold || ''}
                      onChange={e => updateFacNet('late_threshold', e.target.value)}
                      placeholder="08:31"
                    />
                  </div>
                  <Button loading={facNetSaving} onClick={saveFacNetwork}>{ICONS.save} Save Time Settings</Button>
                </>
              ) : (
                <Alert type="error">Failed to load time settings</Alert>
              )}
            </Section>
          </Card>
        )}

        {/* ── Notifications ───────────────────────── */}
        {tab === 'notifs' && (
          <Card>
            <Section title="Excuse Requests">
              <Toggle
                checked={notifs.excuseAlert}
                onChange={v => setNotifs(p => ({ ...p, excuseAlert: v }))}
                label="New Excuse Submitted"
                sublabel="Alert when a student submits an excuse request for your cohort"
              />
            </Section>
            <Section title="Attendance Monitoring">
              <Toggle
                checked={notifs.absentAlert}
                onChange={v => setNotifs(p => ({ ...p, absentAlert: v }))}
                label="High Absence Alert"
                sublabel="Notify when more than 30% of cohort is absent in a session"
              />
              <Toggle
                checked={notifs.sessionEnd}
                onChange={v => setNotifs(p => ({ ...p, sessionEnd: v }))}
                label="Session End Summary"
                sublabel="Get a quick summary when an attendance session closes"
              />
            </Section>
            <Section title="Reports" last>
              <Toggle
                checked={notifs.dailyReport}
                onChange={v => setNotifs(p => ({ ...p, dailyReport: v }))}
                label="Daily Attendance Report"
                sublabel="Receive today's attendance report at end of each school day"
              />
            </Section>
            <Button onClick={() => toast.success('Notification preferences saved')}>{ICONS.save} Save</Button>
          </Card>
        )}

        {/* ── Security ────────────────────────────── */}
        {tab === 'security' && (
          <Card>
            <PasswordSection />
            <div className="h-px bg-gray-100 my-5" />
            <Section title="Active Session" last>
              <div className="px-4 py-3 bg-gray-50 rounded border border-gray-100 mb-3">
                <div className="text-xs font-semibold text-gray-400 mb-1">CURRENT SESSION</div>
                <div className="text-[13px]">Logged in as Facilitator · Session active</div>
                <div className="text-[11px] font-mono text-gray-400 mt-0.5">{format(new Date(), 'dd MMM yyyy, HH:mm')}</div>
              </div>
            </Section>
          </Card>
        )}

        {/* ── Appearance ──────────────────────────── */}
        {tab === 'appearance' && (
          <Card>
            <Section title="Theme" last>
              <div className="flex flex-wrap gap-3 mb-5">
                {[
                  { id: 'light', label: 'Light', sublabel: 'Clean & bright' },
                  { id: 'dark',  label: 'Dark',  sublabel: 'Easy on eyes' },
                ].map(t => (
                  <button
                    key={t.id}
                    onClick={() => { if ((t.id === 'dark') !== dark) toggle() }}
                    className={`flex-1 min-w-[120px] px-3 py-4 rounded-xl cursor-pointer text-center ${(t.id === 'dark') === dark ? 'border-2 border-red bg-red-light' : 'border-2 border-gray-200 bg-gray-50'}`}
                  >
                    <div className={`mb-1.5 ${(t.id === 'dark') === dark ? 'text-red' : 'text-gray-400'}`}>
                      {t.id === 'light' ? (
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
                      ) : (
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
                      )}
                    </div>
                    <div className={`text-[13px] font-semibold ${(t.id === 'dark') === dark ? 'text-red' : 'text-gray-700'}`}>{t.label}</div>
                    <div className="text-[11px] text-gray-400 mt-0.5">{t.sublabel}</div>
                  </button>
                ))}
              </div>
            </Section>
          </Card>
        )}
      </div>
    </>
  )
}

// ────────────────────────────────────────────────────────────────
// SUPER ADMIN SETTINGS
// ────────────────────────────────────────────────────────────────
export function AdminSettings() {
  const { user } = useAuth()
  const { dark, toggle } = useTheme()
  const { settings: schoolSettings, updateSchoolSettings } = useSchool()
  const [tab, setTab] = useState('school')
  const [schoolForm, setSchoolForm] = useState({ school_name: '', school_address: '', school_email: '', school_website: '' })
  const [schoolSaving, setSchoolSaving] = useState(false)
  const [netSettings, setNetSettings] = useState(null)
  const [netLoading, setNetLoading] = useState(true)
  const [netSaving, setNetSaving] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [systemInfo] = useState({
    version: '1.0.0',
    buildDate: '2026-07-28',
    db: 'MongoDB',
    uptime: '8h 42m',
    totalUsers: '—',
    totalSessions: '—',
  })

  useEffect(() => {
    adminApi.getNetworkSettings()
      .then(r => { setNetSettings(r.data); setSchoolForm({ school_name: r.data.school_name || '', school_address: r.data.school_address || '', school_email: r.data.school_email || '', school_website: r.data.school_website || '' }) })
      .catch(() => toast.error('Failed to load network settings'))
      .finally(() => setNetLoading(false))
  }, [])

  const updateNet = (key, value) => setNetSettings(prev => ({ ...prev, [key]: value }))

  const saveNetwork = async () => {
    setNetSaving(true)
    try {
      await adminApi.updateNetworkSettings(netSettings)
      toast.success('Settings saved successfully')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save')
    } finally { setNetSaving(false) }
  }

  const handleExport = async (type) => {
    setExportLoading(true)
    try {
      const res = await import('../../api/client').then(m => m.default.get(`/admin/export/${type}`, { responseType: 'blob' }))
      const url = URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = url; a.download = `attendance-${type}-${format(new Date(), 'yyyy-MM-dd')}.csv`
      a.click(); URL.revokeObjectURL(url)
      toast.success(`${type} data exported`)
    } catch {
      toast.error('Export not yet implemented on server')
    } finally { setExportLoading(false) }
  }

  const tabs = [
    { id: 'school',   icon: ICONS.school, label: 'School Profile' },
    { id: 'policy',   icon: ICONS.policy, label: 'Attendance Policy' },
    { id: 'network',  icon: ICONS.globe, label: 'Network & GPS' },
    { id: 'data',     icon: ICONS.download, label: 'Data & Export' },
    { id: 'security', icon: ICONS.lock, label: 'Security' },
    { id: 'appearance', icon: ICONS.sun, label: 'Appearance' },
    { id: 'system',   icon: ICONS.server, label: 'System Info' },
  ]

  return (
    <>
      <PageHeader title="Settings" subtitle="System-wide administration & configuration" />
      <div className="p-4 sm:p-6 max-w-[700px] animate-fade-in">
        <Tabs tabs={tabs} active={tab} onChange={setTab} />

        {/* ── School Profile ───────────────────────── */}
        {tab === 'school' && (
          <Card>
            <div className="flex flex-wrap items-center gap-5 mb-6">
              <Avatar name={user?.name} size={72} />
              <div className="min-w-0">
                <div className="text-xl font-bold">{user?.name}</div>
                <div className="text-[13px] text-gray-400 font-mono">{user?.email}</div>
                <span className="inline-block mt-1.5 px-2.5 py-[3px] rounded-full bg-[rgba(239,68,68,0.12)] text-[#dc2626] text-[11px] font-semibold border border-[rgba(239,68,68,0.25)]">
                  Super Admin
                </span>
              </div>
            </div>
            <Section title="School Identity">
              <Input label="Institution Name" value={schoolForm.school_name} onChange={e => setSchoolForm(p => ({ ...p, school_name: e.target.value }))} placeholder="Tech School" />
              <Input label="School Address" value={schoolForm.school_address} onChange={e => setSchoolForm(p => ({ ...p, school_address: e.target.value }))} placeholder="Lagos, Nigeria" />
              <Input label="Contact Email" value={schoolForm.school_email} onChange={e => setSchoolForm(p => ({ ...p, school_email: e.target.value }))} placeholder="admin@techschool.edu.ng" />
              <Input label="School Website" value={schoolForm.school_website} onChange={e => setSchoolForm(p => ({ ...p, school_website: e.target.value }))} placeholder="https://techschool.edu.ng" />
              <Button loading={schoolSaving} onClick={async () => {
                setSchoolSaving(true)
                try {
                  await adminApi.updateNetworkSettings(schoolForm)
                  updateSchoolSettings(schoolForm)
                  toast.success('School profile updated')
                } catch (err) { toast.error(err.response?.data?.message || 'Failed to save') }
                finally { setSchoolSaving(false) }
              }}>{ICONS.save} Save Profile</Button>
            </Section>
          </Card>
        )}

        {/* ── Attendance Policy ────────────────────── */}
        {tab === 'policy' && (
          !netLoading ? netSettings && (
            <Card>
              <Section title="Time Windows">
                <p className="text-xs text-gray-400 mb-4 leading-relaxed">
                  Define when the QR attendance session is open, and when a student is marked late.
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-4">
                  <Input
                    label="Session Opens (HH:MM)"
                    value={netSettings.qr_window_start || ''}
                    onChange={e => updateNet('qr_window_start', e.target.value)}
                    placeholder="07:00"
                  />
                  <Input
                    label="Session Closes (HH:MM)"
                    value={netSettings.qr_window_end || ''}
                    onChange={e => updateNet('qr_window_end', e.target.value)}
                    placeholder="10:00"
                  />
                  <Input
                    label="Late After (HH:MM)"
                    value={netSettings.late_threshold || ''}
                    onChange={e => updateNet('late_threshold', e.target.value)}
                    placeholder="08:30"
                  />
                </div>
              </Section>
              <Section title="Attendance Rules" last>
                <Toggle
                  checked={netSettings.network_enforce === 'true'}
                  onChange={v => updateNet('network_enforce', v ? 'true' : 'false')}
                  label="Require School Network"
                  sublabel="Students must be on the school WiFi to mark attendance"
                />
                <Toggle
                  checked={netSettings.geofence_fallback_enabled === 'true'}
                  onChange={v => updateNet('geofence_fallback_enabled', v ? 'true' : 'false')}
                  label="Enable GPS Geofence Fallback"
                  sublabel="Use location check when network validation is unavailable"
                />
              </Section>
              <Button loading={netSaving} onClick={saveNetwork}>{ICONS.save} Save Policy</Button>
            </Card>
          ) : <div className="text-center p-10"><div className="inline-block h-5 w-5 rounded-full border-2 border-gray-200 border-t-red animate-spin" /></div>
        )}

        {/* ── Network & GPS ────────────────────────── */}
        {tab === 'network' && (
          !netLoading ? netSettings && (
            <div className="flex flex-col gap-4">
              <Card>
                <Section title="School Network (WiFi)">
                  <Input
                    label="WiFi Network Name (SSID)"
                    value={netSettings.school_wifi_ssid || ''}
                    onChange={e => updateNet('school_wifi_ssid', e.target.value)}
                    placeholder="e.g. TechSchool-WiFi"
                  />
                  <Input
                    label="IP Range (CIDR notation)"
                    value={netSettings.school_ip_range || ''}
                    onChange={e => updateNet('school_ip_range', e.target.value)}
                    placeholder="e.g. 192.168.1.0/24"
                  />
                  <Alert type="info">
                    The system detects the school network by matching the student's IP address against this range. Make sure it matches your router's DHCP range.
                  </Alert>
                </Section>
              </Card>
              <Card>
                <Section title="GPS Geofence Coordinates" last>
                  <p className="text-xs text-gray-400 mb-4 leading-relaxed">
                    If network detection fails, the system will fall back to GPS coordinates. Set your school's physical location and the acceptable radius.
                  </p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-3">
                    <Input
                      label="Latitude"
                      value={netSettings.school_latitude || ''}
                      onChange={e => updateNet('school_latitude', e.target.value)}
                      placeholder="6.5244"
                    />
                    <Input
                      label="Longitude"
                      value={netSettings.school_longitude || ''}
                      onChange={e => updateNet('school_longitude', e.target.value)}
                      placeholder="3.3792"
                    />
                    <Input
                      label="Radius (meters)"
                      value={netSettings.school_geofence_radius_meters || ''}
                      onChange={e => updateNet('school_geofence_radius_meters', e.target.value)}
                      placeholder="150"
                    />
                  </div>
                </Section>
                <Button loading={netSaving} onClick={saveNetwork}>{ICONS.save} Save Network & GPS Settings</Button>
              </Card>
            </div>
          ) : <div className="text-center p-10"><div className="inline-block h-5 w-5 rounded-full border-2 border-gray-200 border-t-red animate-spin" /></div>
        )}

        {/* ── Data & Export ────────────────────────── */}
        {tab === 'data' && (
          <div className="flex flex-col gap-4">
            <Card>
              <Section title="Export Attendance Data">
                <p className="text-xs text-gray-400 mb-4 leading-relaxed">
                  Download attendance records in CSV format for offline analysis, reporting, or archiving.
                </p>
                <div className="flex flex-col gap-2.5">
                  {[
                    { key: 'attendance', icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-400)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>, label: 'All Attendance Records', desc: 'Full history of every scan and manual entry' },
                    { key: 'students',   icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-400)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>, label: 'Student Roster',         desc: 'All registered students with cohort info' },
                    { key: 'excuses',    icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-400)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>, label: 'Excuse Requests',         desc: 'All excuse submissions and their statuses' },
                    { key: 'audit',      icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-400)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>, label: 'Audit Logs',              desc: 'Admin and facilitator action trail' },
                  ].map(item => (
                    <div key={item.key} className="flex items-center gap-3.5 px-4 py-3.5 rounded-xl bg-gray-50 border border-gray-100 flex-wrap">
                      <span className="text-2xl">{item.icon}</span>
                      <div className="flex-1 min-w-0">
                        <div className="text-[13px] font-semibold">{item.label}</div>
                        <div className="text-[11px] text-gray-400 mt-0.5">{item.desc}</div>
                      </div>
                      <button
                        onClick={() => handleExport(item.key)}
                        disabled={exportLoading}
                        className="px-3.5 py-[7px] rounded text-xs font-semibold bg-white text-gray-700 border border-gray-200 cursor-pointer disabled:opacity-50"
                      >
                        Export CSV
                      </button>
                    </div>
                  ))}
                </div>
              </Section>
            </Card>
            <Card className="border-[#fca5a5] bg-[rgba(254,226,226,0.5)]">
              <Section title={<span><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#dc2626" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: 6, verticalAlign: 'middle' }}><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg> Danger Zone</span>} last>
                <Alert type="warning">
                  These actions are permanent and cannot be undone. Only proceed if you are absolutely certain.
                </Alert>
                <div className="flex gap-2.5 mt-3 flex-wrap">
                  <button
                    onClick={() => toast.error('Disabled in this environment')}
                    className="px-4 py-2 rounded text-xs font-semibold bg-transparent text-[#dc2626] border border-[#fca5a5] cursor-pointer"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg> Clear All Attendance (Current Month)
                  </button>
                </div>
              </Section>
            </Card>
          </div>
        )}

        {/* ── Security ────────────────────────────── */}
        {tab === 'security' && (
          <Card>
            <PasswordSection />
            <div className="h-px bg-gray-100 my-5" />
            <Section title="Admin Access" last>
              <Alert type="warning">
                As Super Admin you have unrestricted system access. All actions are logged in the Audit trail.
              </Alert>
              <div className="px-4 py-3 bg-gray-50 rounded border border-gray-100 mt-3">
                <div className="text-xs font-semibold text-gray-400 mb-1">LOGGED IN AS</div>
                <div className="text-[13px] font-medium">{user?.name}</div>
                <div className="text-[11px] font-mono text-gray-400">{user?.email}</div>
                <div className="text-[11px] text-gray-400 mt-1">{format(new Date(), 'dd MMM yyyy, HH:mm')}</div>
              </div>
            </Section>
          </Card>
        )}

        {/* ── Appearance ──────────────────────────── */}
        {tab === 'appearance' && (
          <Card>
            <Section title="Theme" last>
              <div className="flex flex-wrap gap-3">
                {[
                  { id: 'light', label: 'Light', sublabel: 'Clean & bright' },
                  { id: 'dark',  label: 'Dark',  sublabel: 'Easy on eyes' },
                ].map(t => (
                  <button
                    key={t.id}
                    onClick={() => { if ((t.id === 'dark') !== dark) toggle() }}
                    className={`flex-1 min-w-[120px] px-3 py-4 rounded-xl cursor-pointer text-center ${(t.id === 'dark') === dark ? 'border-2 border-red bg-red-light' : 'border-2 border-gray-200 bg-gray-50'}`}
                  >
                    <div className={`mb-1.5 ${(t.id === 'dark') === dark ? 'text-red' : 'text-gray-400'}`}>
                      {t.id === 'light' ? (
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
                      ) : (
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
                      )}
                    </div>
                    <div className={`text-[13px] font-semibold ${(t.id === 'dark') === dark ? 'text-red' : 'text-gray-700'}`}>{t.label}</div>
                    <div className="text-[11px] text-gray-400 mt-0.5">{t.sublabel}</div>
                  </button>
                ))}
              </div>
            </Section>
          </Card>
        )}

        {/* ── System Info ──────────────────────────── */}
        {tab === 'system' && (
          <div className="flex flex-col gap-4">
            <Card>
              <Section title="Application">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {[
                    { label: 'Version', value: systemInfo.version },
                    { label: 'Build Date', value: systemInfo.buildDate },
                    { label: 'Database', value: systemInfo.db },
                    { label: 'Backend', value: 'Spring Boot 3.2' },
                    { label: 'Frontend', value: 'React + Vite 5' },
                    { label: 'Auth', value: 'JWT + WebAuthn' },
                  ].map(({ label, value }) => (
                    <div key={label} className="px-3.5 py-3 bg-gray-50 rounded border border-gray-100">
                      <div className="text-[10px] font-bold tracking-wide uppercase text-gray-400 mb-1">{label}</div>
                      <div className="text-[13px] font-mono">{value}</div>
                    </div>
                  ))}
                </div>
              </Section>
            </Card>
            <Card>
              <Section title="Quick Health Check" last>
                {[
                  { label: 'Backend API', status: true, detail: 'http://localhost:8080/api' },
                  { label: 'MongoDB', status: true, detail: 'localhost:27017' },
                  { label: 'QR Engine', status: true, detail: 'ZXing 3.5.3' },
                  { label: 'WebAuthn', status: isWebAuthnSupported(), detail: isWebAuthnSupported() ? 'Supported' : 'Not supported' },
                ].map(({ label, status, detail }) => (
                  <div key={label} className="flex items-center gap-2.5 py-2.5 border-b border-gray-50">
                    <div className={`w-2 h-2 rounded-full shrink-0 ${status ? 'bg-[#22c55e]' : 'bg-[#ef4444]'}`} />
                    <div className="flex-1 min-w-0">
                      <div className="text-[13px] font-medium">{label}</div>
                      <div className="text-[11px] font-mono text-gray-400">{detail}</div>
                    </div>
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${status ? 'bg-green-light text-green-dark' : 'bg-red-light text-red'}`}>{status ? 'OK' : 'Offline'}</span>
                  </div>
                ))}
              </Section>
            </Card>
          </div>
        )}
      </div>
    </>
  )
}

function isWebAuthnSupported() {
  return typeof window !== 'undefined' && window.PublicKeyCredential !== undefined
}
