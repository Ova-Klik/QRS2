import React, { useState, useEffect, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { studentApi } from '../../api/client'
import { authApi } from '../../api/client'
import { Card, StatCard, Badge, Table, PageHeader, LoadingPage, Alert, Button, Input, Modal, Select, Textarea } from '../../components/common/UI'
import { isWebAuthnSupported, isPlatformAuthenticatorAvailable, registerBiometric, authenticateBiometric, getNetworkInfo } from '../../utils/webauthn'
import { Html5Qrcode } from 'html5-qrcode'
import toast from 'react-hot-toast'
import { format } from 'date-fns'

// ── Dashboard ────────────────────────────────────────────
export function StudentDashboard() {
  const [data, setData]     = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    studentApi.dashboard().then(r => setData(r.data)).catch(() => toast.error('Failed to load dashboard')).finally(() => setLoading(false))
  }, [])

  if (loading) return <LoadingPage />

  const d = data || {}
  return (
    <>
      <PageHeader title="Dashboard" subtitle={`Today — ${format(new Date(), 'dd MMM yyyy')}`} />
      <div style={{ padding: 24 }} className="fade-in">
        {d.markedToday
          ? <Alert type="success"><strong>Attendance recorded today</strong> — You are marked <strong>{d.todayStatus?.toLowerCase()}</strong>.</Alert>
          : <Alert type="info"><strong>Attendance not yet recorded today.</strong> Go to Scan QR Code to mark attendance.</Alert>
        }
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12, marginBottom: 20 }}>
          <StatCard label="Attendance Rate" value={`${Math.round(d.rate || 0)}%`} progress={d.rate} />
          <StatCard label="Present"  value={d.present  || 0} badge="On time"  badgeColor="green" />
          <StatCard label="Late"     value={d.late     || 0} badge="After 7:30" badgeColor="yellow" />
          <StatCard label="Absent"   value={d.absent   || 0} badge="No record"  badgeColor="red" color={d.absent > 0 ? 'var(--red)' : undefined} />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Card>
            <div style={{ fontWeight: 600, marginBottom: 14 }}>Registered Device</div>
            {d.deviceStatus?.registered ? (
              <>
                <div style={{ fontSize: 11, color: 'var(--gray-400)', marginBottom: 4 }}>FINGERPRINT</div>
                <div style={{ fontFamily: 'var(--mono)', fontSize: 12, color: 'var(--gray-600)', marginBottom: 10 }}>{d.deviceStatus.fingerprint}</div>
                <span style={{ display: 'inline-block', fontSize: 10, padding: '2px 7px', borderRadius: 20, background: 'var(--green-light)', color: 'var(--green-dark)', border: '1px solid #a8dbb8', fontWeight: 500 }}>Registered</span>
              </>
            ) : <Alert type="warning">No device registered. Contact admin.</Alert>}
          </Card>
          <Card>
            <div style={{ fontWeight: 600, marginBottom: 14 }}>Recent History</div>
            {(d.recentHistory || []).slice(0, 5).map((r, i) => (
              <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: i < 4 ? '1px solid var(--gray-50)' : 'none' }}>
                <span style={{ fontSize: 12, color: 'var(--gray-600)' }}>{r.date ? format(new Date(r.date), 'dd MMM') : '—'}</span>
                <Badge status={r.status} />
              </div>
            ))}
            {!(d.recentHistory?.length) && <p style={{ fontSize: 12, color: 'var(--gray-400)' }}>No history yet</p>}
          </Card>
        </div>
      </div>
    </>
  )
}

// ── Scan QR ──────────────────────────────────────────────
export function StudentScan() {
  const [searchParams] = useSearchParams()
  const [loading, setLoading]   = useState(false)
  const [result, setResult]     = useState(null)
  const [token, setToken]       = useState(searchParams.get('token') || '')
  const [dashboard, setDash]    = useState(null)
  const [biometricRegistered, setBiometricRegistered] = useState(false)
  const [biometricAvailable, setBiometricAvailable] = useState(false)
  const [networkInfo, setNetworkInfo] = useState({ ssid: null, online: navigator.onLine })
  const [biometricLoading, setBiometricLoading] = useState(false)
  const [cameraActive, setCameraActive] = useState(false)
  const [scannerError, setScannerError] = useState(null)
  const scannerRef = useRef(null)
  const html5QrRef = useRef(null)

  useEffect(() => {
    studentApi.dashboard().then(r => {
      setDash(r.data)
      setBiometricRegistered(!!r.data?.biometricRegistered)
    }).catch(() => {})

    isPlatformAuthenticatorAvailable().then(avail => {
      setBiometricAvailable(avail)
    }).catch(() => {})

    const net = getNetworkInfo()
    setNetworkInfo({ ssid: net.ssid, online: navigator.onLine })

    const handleOnline = () => setNetworkInfo(prev => ({ ...prev, online: true }))
    const handleOffline = () => setNetworkInfo(prev => ({ ...prev, online: false }))
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      stopCamera()
    }
  }, [])

  const startCamera = async () => {
    setScannerError(null)
    try {
      const html5Qr = new Html5Qrcode('qr-reader')
      html5QrRef.current = html5Qr

      await html5Qr.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 220, height: 220 }, aspectRatio: 1.0 },
        (decodedText) => {
          let scanned = decodedText.trim()
          const qrsMatch = scanned.match(/[?&]qrs=([^&]+)/)
          if (qrsMatch) {
            scanned = qrsMatch[1]
          } else if (scanned.startsWith('QRS:')) {
            scanned = scanned.substring(4)
          }
          if (scanned && !loading) {
            setToken(scanned)
            stopCamera()
            toast.success('QR code scanned!')
            submitScan(scanned)
          }
        },
        () => {}
      )
      setCameraActive(true)
    } catch (err) {
      setScannerError(err?.message || 'Could not access camera. Please allow camera permissions.')
      setCameraActive(false)
    }
  }

  const stopCamera = () => {
    if (html5QrRef.current) {
      html5QrRef.current.stop().then(() => {
        html5QrRef.current.clear()
        html5QrRef.current = null
        setCameraActive(false)
      }).catch(() => {
        html5QrRef.current = null
        setCameraActive(false)
      })
    }
  }

  const getFingerprint = () => {
    const nav = window.navigator
    const fp = btoa([nav.userAgent, nav.language, screen.width, screen.height, nav.hardwareConcurrency].join('|'))
    return fp.substring(0, 32)
  }

  const submitScan = async (scanToken) => {
    const tok = scanToken || token
    if (!tok.trim()) { toast.error('Enter or scan a QR token'); return }

    let biometricResult = null
    if (biometricRegistered) {
      setBiometricLoading(true)
      try {
        toast.loading('Verifying fingerprint...', { id: 'bio' })
        biometricResult = await authenticateBiometric(dashboard?.webAuthnCredentialId)
        toast.success('Fingerprint verified', { id: 'bio' })
      } catch (err) {
        toast.error('Fingerprint verification failed: ' + (err.message || 'Please try again'), { id: 'bio' })
        setBiometricLoading(false)
        return
      }
      setBiometricLoading(false)
    }

    setLoading(true)
    try {
      let coords = { latitude: null, longitude: null }
      if ('geolocation' in navigator) {
        try {
          const pos = await new Promise((resolve) => {
            navigator.geolocation.getCurrentPosition(
              p => resolve(p.coords),
              () => resolve(null),
              { enableHighAccuracy: true, timeout: 3000 }
            )
          })
          if (pos) {
            coords.latitude = pos.latitude
            coords.longitude = pos.longitude
          }
        } catch (e) {}
      }

      const payload = {
        token: tok.trim(),
        deviceFingerprint: getFingerprint(),
        userAgent: navigator.userAgent,
        networkSSID: networkInfo.ssid,
        clientIP: null,
        latitude: coords.latitude,
        longitude: coords.longitude,
        biometricVerified: !!biometricResult,
        biometricCredentialId: biometricResult?.credentialId || null,
        biometricAuthenticatorData: biometricResult?.authenticatorData || null,
        biometricClientDataJSON: biometricResult?.clientDataJSON || null,
        biometricSignature: biometricResult?.signature || null,
      }
      const { data } = await studentApi.scan(payload)
      setResult(data)
      toast.success(`Marked ${data.status?.toLowerCase()}!`)
      studentApi.dashboard().then(r => {
        setDash(r.data)
        setBiometricRegistered(!!r.data?.biometricRegistered)
      })
    } catch (err) {
      toast.error(err.response?.data?.message || 'Scan failed')
    } finally {
      setLoading(false)
    }
  }

  const handleSetupBiometric = async () => {
    setBiometricLoading(true)
    try {
      toast.loading('Starting biometric registration...', { id: 'bio-reg' })
      const reg = await registerBiometric(dashboard?.userId || 'current-user')
      await authApi.webauthnRegister({
        credentialId: reg.credentialId,
        publicKey: reg.publicKey,
      })
      setBiometricRegistered(true)
      toast.success('Fingerprint registered successfully!', { id: 'bio-reg' })
      studentApi.dashboard().then(r => {
        setDash(r.data)
        setBiometricRegistered(true)
      })
    } catch (err) {
      toast.error('Biometric registration failed: ' + (err.message || 'Try again'), { id: 'bio-reg' })
    } finally {
      setBiometricLoading(false)
    }
  }

  const checks = [
    { label: 'Device registered',          pass: !!dashboard?.deviceStatus?.registered },
    { label: dashboard?.markedToday ? 'Marked for today' : 'Not yet marked today', pass: dashboard?.markedToday || !dashboard?.markedToday, green: !!dashboard?.markedToday },
    { label: 'School network connected',    pass: networkInfo.online },
    { label: 'Biometric verified',          pass: biometricRegistered ? false : true, optional: !biometricRegistered },
  ]

  return (
    <>
      <PageHeader title="Scan QR Code" subtitle="Mark your attendance for today's session" />
      <div style={{ padding: 24, maxWidth: 560 }} className="fade-in">
        {result && (
          <Alert type={result.status === 'PRESENT' ? 'success' : 'warning'}>
            <strong>Attendance marked — {result.status}</strong><br />
            {result.markedAt && <span style={{ fontSize: 12 }}>Recorded at {format(new Date(result.markedAt), 'HH:mm:ss')}</span>}
          </Alert>
        )}
        {dashboard?.markedToday && !result && (
          <Alert type="success"><strong>Already marked today — {dashboard.todayStatus}</strong></Alert>
        )}

        {biometricAvailable && !biometricRegistered && !dashboard?.markedToday && !result && (
          <Alert type="info">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
              <div>
                <strong>Set up fingerprint authentication</strong><br />
                <span style={{ fontSize: 12 }}>Secure your attendance with biometric verification</span>
              </div>
              <Button size="sm" variant="outline" loading={biometricLoading} onClick={handleSetupBiometric}>
                Set up fingerprint
              </Button>
            </div>
          </Alert>
        )}

        {/* Network Status */}
        <Card style={{ marginBottom: 16, padding: '12px 16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: networkInfo.online ? 'var(--green)' : 'var(--red)' }} />
              <span style={{ fontSize: 12, fontWeight: 500 }}>
                {networkInfo.online ? 'Connected to network' : 'No network connection'}
              </span>
            </div>
            <span style={{ fontSize: 10, color: 'var(--gray-400)', fontFamily: 'var(--mono)' }}>
              {networkInfo.ssid || 'Detecting...'}
            </span>
          </div>
        </Card>

        {/* Camera Scanner */}
        <Card style={{ marginBottom: 16, overflow: 'hidden' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
            <div style={{ fontWeight: 600 }}>QR Scanner</div>
            {cameraActive && (
              <Button size="sm" variant="outline" onClick={stopCamera}>Stop Camera</Button>
            )}
          </div>

          <div id="qr-reader" ref={scannerRef} style={{ width: '100%', borderRadius: 'var(--radius)', overflow: 'hidden', marginBottom: 12 }} />

          {!cameraActive && !result && !dashboard?.markedToday && (
            <div style={{ textAlign: 'center' }}>
              <div
                style={{
                  width: 80, height: 80, borderRadius: '50%', display: 'flex', alignItems: 'center',
                  justifyContent: 'center', margin: '0 auto 14px', cursor: 'pointer',
                  background: 'var(--red-light)', transition: 'transform .15s',
                }}
                onClick={startCamera}
                onMouseEnter={e => e.currentTarget.style.transform = 'scale(1.05)'}
                onMouseLeave={e => e.currentTarget.style.transform = 'scale(1)'}
              >
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--red)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
                  <circle cx="12" cy="13" r="4"/>
                </svg>
              </div>
              <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 6 }}>Scan QR Code with Camera</h3>
              <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 16 }}>
                Point your camera at the facilitator's QR code to mark attendance
              </p>
              <Button onClick={startCamera} style={{ width: '100%', justifyContent: 'center' }}>
                Open Camera
              </Button>
              {scannerError && (
                <p style={{ fontSize: 11, color: 'var(--red)', marginTop: 8 }}>{scannerError}</p>
              )}
            </div>
          )}

          {cameraActive && (
            <p style={{ fontSize: 11, color: 'var(--gray-400)', textAlign: 'center', marginTop: 8 }}>
              Align the QR code within the frame. Scanning happens automatically.
            </p>
          )}
        </Card>

        {/* Manual Token Entry */}
        <Card style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 600, marginBottom: 12 }}>Manual Entry</div>
          <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 12 }}>
            If camera scanning is unavailable, paste the QR token below.
          </p>
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              value={token} onChange={e => setToken(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !loading && submitScan()}
              placeholder="Paste QR token here..."
              style={{ flex: 1, padding: '10px 14px', border: '1.5px solid var(--gray-200)', borderRadius: 'var(--radius)', fontSize: 13, fontFamily: 'var(--mono)' }}
            />
            <Button loading={loading || biometricLoading} onClick={() => submitScan()} disabled={dashboard?.markedToday || biometricLoading}>
              {biometricRegistered ? 'Verify & Mark' : 'Mark'}
            </Button>
          </div>
          {biometricRegistered && (
            <p style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: 10 }}>
              Fingerprint verification will be required before marking attendance
            </p>
          )}
        </Card>

        <Card>
          <div style={{ fontWeight: 600, marginBottom: 12 }}>Validation Checklist</div>
          {checks.map((c, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: i < checks.length - 1 ? '1px solid var(--gray-50)' : 'none' }}>
              <div style={{ width: 20, height: 20, borderRadius: '50%', background: c.green ? 'var(--green-light)' : c.pass ? 'var(--green-light)' : 'var(--red-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <span style={{ fontSize: 10, color: c.green ? 'var(--green-dark)' : c.pass ? 'var(--green-dark)' : 'var(--red)' }}>{c.pass ? '\u2713' : '\u2717'}</span>
              </div>
              <span style={{ fontSize: 13, fontWeight: c.green ? 600 : 400, color: c.green ? 'var(--green-dark)' : c.pass ? 'var(--gray-900)' : 'var(--gray-400)' }}>
                {c.label}
                {c.optional && <span style={{ fontSize: 10, color: 'var(--gray-300)', marginLeft: 6 }}>(recommended)</span>}
              </span>
            </div>
          ))}
        </Card>
      </div>
    </>
  )
}

// ── History ──────────────────────────────────────────────
export function StudentHistory() {
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    studentApi.history().then(r => setRecords(r.data)).catch(() => toast.error('Failed to load history')).finally(() => setLoading(false))
  }, [])

  if (loading) return <LoadingPage />

  const present = records.filter(r => r.status === 'PRESENT').length
  const late    = records.filter(r => r.status === 'LATE').length
  const absent  = records.filter(r => r.status === 'ABSENT').length
  const excused = records.filter(r => r.status === 'EXCUSED').length
  const rate    = records.length ? Math.round((present + late) / records.length * 100) : 0

  const cols = [
    { key: 'date',         label: 'Date',     strong: true, render: v => v ? format(new Date(v), 'dd MMM yyyy') : '—' },
    { key: 'markedAt',     label: 'Time',     render: v => v ? <span style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>{format(new Date(v), 'HH:mm')}</span> : '—' },
    { key: 'cohortName',   label: 'Cohort' },
    { key: 'status',       label: 'Status',   render: v => <Badge status={v} /> },
    { key: 'manual',       label: 'Type',     render: v => <Badge status={v ? 'MANUAL' : 'ACTIVE'} /> },
    { key: 'manualReason', label: 'Note',     render: v => v ? <span style={{ fontSize: 11, color: 'var(--gray-400)' }}>{v}</span> : null },
  ]

  return (
    <>
      <PageHeader title="My Attendance" subtitle={`${rate}% attendance rate · ${records.length} records`} />
      <div style={{ padding: 24 }} className="fade-in">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 20 }}>
          <StatCard label="Present" value={present} badgeColor="green" />
          <StatCard label="Late"    value={late}    badgeColor="yellow" />
          <StatCard label="Absent"  value={absent}  badgeColor="red" />
          <StatCard label="Excused" value={excused} />
        </div>
        <Card>
          <Table columns={cols} rows={[...records].reverse()} emptyMessage="No attendance records yet" />
        </Card>
      </div>
    </>
  )
}

// ── Settings ─────────────────────────────────────────────
export function StudentSettings() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [loading, setLoading] = useState(false)
  const [biometricRegistered, setBiometricRegistered] = useState(false)
  const [biometricLoading, setBiometricLoading] = useState(false)
  const [platformAuth, setPlatformAuth] = useState(false)
  const { user } = require('../../context/AuthContext').useAuth ? require('../../context/AuthContext').useAuth() : { user: null }

  useEffect(() => {
    isPlatformAuthenticatorAvailable().then(avail => setPlatformAuth(avail)).catch(() => {})
    studentApi.dashboard().then(r => {
      setBiometricRegistered(!!r.data?.biometricRegistered)
    }).catch(() => {})
  }, [])

  const handleChange = async e => {
    e.preventDefault()
    if (form.newPassword !== form.confirm) { toast.error('Passwords do not match'); return }
    setLoading(true)
    try {
      const api = (await import('../../api/client')).default
      await api.post('/auth/change-password', { currentPassword: form.currentPassword, newPassword: form.newPassword })
      toast.success('Password changed successfully')
      setForm({ currentPassword: '', newPassword: '', confirm: '' })
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to change password')
    } finally {
      setLoading(false)
    }
  }

  const handleRegisterBiometric = async () => {
    setBiometricLoading(true)
    try {
      toast.loading('Place your finger on the sensor...', { id: 'bio-settings' })
      const reg = await registerBiometric(user?.userId || user?.id || 'current-user')
      await authApi.webauthnRegister({
        credentialId: reg.credentialId,
        publicKey: reg.publicKey,
      })
      setBiometricRegistered(true)
      toast.success('Fingerprint registered successfully!', { id: 'bio-settings' })
    } catch (err) {
      toast.error('Registration failed: ' + (err.message || 'Try again'), { id: 'bio-settings' })
    } finally {
      setBiometricLoading(false)
    }
  }

  return (
    <>
      <PageHeader title="Settings" subtitle="Manage your account" />
      <div style={{ padding: 24, maxWidth: 480 }} className="fade-in">
        {/* Biometric Settings */}
        <Card style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>Biometric Authentication</div>
          <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 16 }}>
            Register your fingerprint for secure attendance marking.
          </p>
          {platformAuth ? (
            biometricRegistered ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ width: 24, height: 24, borderRadius: '50%', background: 'var(--green-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <span style={{ fontSize: 12, color: 'var(--green-dark)' }}>{'\u2713'}</span>
                </div>
                <span style={{ fontSize: 13, color: 'var(--green-dark)', fontWeight: 500 }}>Fingerprint registered</span>
              </div>
            ) : (
              <Button variant="outline" loading={biometricLoading} onClick={handleRegisterBiometric}>
                Register fingerprint
              </Button>
            )
          ) : (
            <Alert type="warning">Biometric authentication is not available on this device or browser.</Alert>
          )}
        </Card>

        {/* Password Change */}
        <Card>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>Change Password</div>
          <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 20 }}>Use a strong password of at least 6 characters.</p>
          <form onSubmit={handleChange}>
            <Input label="Current password" type="password" value={form.currentPassword} onChange={e => setForm(p => ({ ...p, currentPassword: e.target.value }))} />
            <Input label="New password" type="password" value={form.newPassword} onChange={e => setForm(p => ({ ...p, newPassword: e.target.value }))} />
            <Input label="Confirm new password" type="password" value={form.confirm} onChange={e => setForm(p => ({ ...p, confirm: e.target.value }))} />
            <Button type="submit" loading={loading}>Update Password</Button>
          </form>
        </Card>
      </div>
    </>
  )
}

// ── Excuse Requests ──────────────────────────────────────
export function StudentExcuse() {
  const [requests, setRequests] = useState([])
  const [loading, setLoading]   = useState(true)
  const [modal, setModal]       = useState(false)
  const [saving, setSaving]     = useState(false)
  const [form, setForm]         = useState({
    reason: '',
    numberOfDays: 1,
    startDate: format(new Date(), 'yyyy-MM-dd'),
    coverUpPlan: ''
  })

  const load = useCallback(() => {
    studentApi.myExcuses()
      .then(r => setRequests(r.data))
      .catch(() => toast.error('Failed to load excuse requests'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  const submit = async () => {
    if (!form.reason.trim() || !form.coverUpPlan.trim()) {
      toast.error('Reason and Cover-up Plan are required')
      return
    }
    setSaving(true)
    try {
      await studentApi.submitExcuse(form)
      toast.success('Excuse request submitted successfully')
      setModal(false)
      setForm({ reason: '', numberOfDays: 1, startDate: format(new Date(), 'yyyy-MM-dd'), coverUpPlan: '' })
      load()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit request')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingPage />

  return (
    <>
      <PageHeader title="Excuse Requests" subtitle="Request absence excuses and track review status"
        actions={<Button size="sm" onClick={() => setModal(true)}>+ Request Excuse</Button>} />
      <div style={{ padding: 24 }} className="fade-in">
        <Card>
          <Table
            columns={[
              { key: 'startDate',    label: 'Start Date', render: v => v ? format(new Date(v), 'dd MMM yyyy') : '—' },
              { key: 'numberOfDays', label: 'Days',       render: v => `${v} day${v > 1 ? 's' : ''}` },
              { key: 'reason',       label: 'Reason',     strong: true },
              { key: 'coverUpPlan',  label: 'Cover-Up Plan', render: v => <span style={{ fontSize: 12, color: 'var(--gray-600)' }}>{v}</span> },
              { key: 'status',       label: 'Status',     render: v => <Badge status={v} /> },
              { key: 'reviewerNotes',label: 'Reviewer Feedback', render: (v, row) => row.reviewedByName ? `${row.reviewedByName}: ${v || 'No notes'}` : '—' },
            ]}
            rows={requests}
            emptyMessage="No excuse requests submitted yet"
          />
        </Card>
      </div>

      <Modal open={modal} onClose={() => setModal(false)} title="Request Absence Excuse">
        <Input label="Reason for Absence *" value={form.reason} onChange={e => setForm(p => ({ ...p, reason: e.target.value }))} placeholder="e.g. Medical emergency / Family event" />
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Input label="Start Date *" type="date" value={form.startDate} onChange={e => setForm(p => ({ ...p, startDate: e.target.value }))} />
          <Input label="Number of Days *" type="number" min="1" max="30" value={form.numberOfDays} onChange={e => setForm(p => ({ ...p, numberOfDays: parseInt(e.target.value) || 1 }))} />
        </div>
        <Textarea label="Plan to Cover Missed Lessons *" value={form.coverUpPlan} onChange={e => setForm(p => ({ ...p, coverUpPlan: e.target.value }))} placeholder="Detail how you will study and submit assignments for missed classes..." rows={3} />
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16 }}>
          <Button variant="outline" onClick={() => setModal(false)}>Cancel</Button>
          <Button loading={saving} onClick={submit}>Submit Request</Button>
        </div>
      </Modal>
    </>
  )
}
