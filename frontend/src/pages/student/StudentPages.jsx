import React, { useState, useEffect, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { studentApi } from '../../api/client'
import { authApi } from '../../api/client'
import { Card, StatCard, Badge, Table, PageHeader, LoadingPage, Alert, Button, Input, Modal, Select, Textarea, Pagination } from '../../components/common/UI'
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
      <div className="p-4 sm:p-6 animate-fade-in">
        {d.markedToday
          ? <Alert type="success"><strong>Attendance recorded today</strong> — You are marked <strong>{d.todayStatus?.toLowerCase()}</strong>.</Alert>
          : <Alert type="info"><strong>Attendance not yet recorded today.</strong> Go to Scan QR Code to mark attendance.</Alert>
        }
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3 mb-5">
          <StatCard label="Attendance Rate" value={`${Math.round(d.rate || 0)}%`} progress={d.rate} />
          <StatCard label="Present"  value={d.present  || 0} badge="On time"  badgeColor="green" />
          <StatCard label="Late"     value={d.late     || 0} badge="After 7:30" badgeColor="yellow" />
          <StatCard label="Absent"   value={d.absent   || 0} badge="No record"  badgeColor="red" color={d.absent > 0 ? 'var(--red)' : undefined} />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Card>
            <div className="font-semibold mb-3.5">Registered Device</div>
            {d.deviceStatus?.registered ? (
              <>
                <div className="text-[11px] text-gray-400 mb-1">FINGERPRINT</div>
                <div className="font-mono text-xs text-gray-600 mb-2.5 break-all">{d.deviceStatus.fingerprint}</div>
                <span className="inline-block text-[10px] px-[7px] py-0.5 rounded-full bg-green-light text-green-dark border border-[#a8dbb8] font-medium">Registered</span>
              </>
            ) : <Alert type="warning">No device registered. Contact admin.</Alert>}
          </Card>
          <Card>
            <div className="font-semibold mb-3.5">Recent History</div>
            {(d.recentHistory || []).slice(0, 5).map((r, i) => (
              <div key={i} className={`flex items-center justify-between py-1.5 ${i < 4 ? 'border-b border-gray-50' : ''}`}>
                <span className="text-xs text-gray-600 min-w-0 truncate">{r.date ? format(new Date(r.date), 'dd MMM') : '—'}</span>
                <Badge status={r.status} />
              </div>
            ))}
            {!(d.recentHistory?.length) && <p className="text-xs text-gray-400">No history yet</p>}
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
  const [autoScanning, setAutoScanning] = useState(false)
  const scannerRef = useRef(null)
  const html5QrRef = useRef(null)
  const autoScanDoneRef = useRef(false)

  const scanTokenFromUrl = searchParams.get('token')

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

  useEffect(() => {
    if (scanTokenFromUrl && !autoScanDoneRef.current) {
      autoScanDoneRef.current = true
      setAutoScanning(true)
      setToken(scanTokenFromUrl)
      const t = setTimeout(() => {
        submitScan(scanTokenFromUrl)
        setAutoScanning(false)
      }, 600)
      return () => clearTimeout(t)
    }
  }, [scanTokenFromUrl])

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
      <div className="p-4 sm:p-6 max-w-[560px] animate-fade-in">
        {result && (
          <Alert type={result.status === 'PRESENT' ? 'success' : 'warning'}>
            <strong>Attendance marked — {result.status}</strong><br />
            {result.markedAt && <span className="text-xs">Recorded at {format(new Date(result.markedAt), 'HH:mm:ss')}</span>}
          </Alert>
        )}
        {dashboard?.markedToday && !result && (
          <Alert type="success"><strong>Already marked today — {dashboard.todayStatus}</strong></Alert>
        )}

        {biometricAvailable && !biometricRegistered && !dashboard?.markedToday && !result && (
          <Alert type="info">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <div className="min-w-0">
                <strong>Set up fingerprint authentication</strong><br />
                <span className="text-xs">Secure your attendance with biometric verification</span>
              </div>
              <Button size="sm" variant="outline" loading={biometricLoading} onClick={handleSetupBiometric}>
                Set up fingerprint
              </Button>
            </div>
          </Alert>
        )}

        {/* Network Status */}
        <Card className="mb-4 !px-4 !py-3">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <div className="flex items-center gap-2 min-w-0">
              <div className={`w-2 h-2 rounded-full shrink-0 ${networkInfo.online ? 'bg-green' : 'bg-red'}`} />
              <span className="text-xs font-medium truncate">
                {networkInfo.online ? 'Connected to network' : 'No network connection'}
              </span>
            </div>
            <span className="text-[10px] text-gray-400 font-mono">
              {networkInfo.ssid || 'Detecting...'}
            </span>
          </div>
        </Card>

        {/* Camera Scanner */}
        <Card className="mb-4 overflow-hidden">
          <div className="flex items-center justify-between gap-2 mb-4 flex-wrap">
            <div className="font-semibold">QR Scanner</div>
            {cameraActive && (
              <Button size="sm" variant="outline" onClick={stopCamera}>Stop Camera</Button>
            )}
          </div>

          <div id="qr-reader" ref={scannerRef} className="w-full rounded overflow-hidden mb-3" />

          {autoScanning && (
            <Alert type="info" ><strong>QR detected — marking your attendance...</strong></Alert>
          )}
          {!cameraActive && !result && !dashboard?.markedToday && (
            <div className="text-center">
              <div
                className="w-24 h-24 rounded-full flex items-center justify-center mx-auto mb-4 cursor-pointer bg-red-light transition-transform duration-150"
                onClick={startCamera}
                onMouseEnter={e => e.currentTarget.style.transform = 'scale(1.05)'}
                onMouseLeave={e => e.currentTarget.style.transform = 'scale(1)'}
              >
                <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="var(--red)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
                  <circle cx="12" cy="13" r="4"/>
                </svg>
              </div>
              <h3 className="text-[15px] font-semibold mb-1.5">Scan QR Code with Camera</h3>
              <p className="text-xs text-gray-400 mb-4">
                Point your camera at the facilitator's QR code to mark attendance
              </p>
              <Button onClick={startCamera} className="animate-sign-pulse text-lg font-semibold !px-7 !py-4 !rounded-[12px] hover:scale-105 hover:shadow-[0_10px_24px_rgba(192,57,43,0.28)] active:scale-95 w-full justify-center">
                Open Camera &amp; Sign Attendance
              </Button>
              {scannerError && (
                <p className="text-[11px] text-red mt-2">{scannerError}</p>
              )}
            </div>
          )}

          {cameraActive && (
            <p className="text-[11px] text-gray-400 text-center mt-2">
              Align the QR code within the frame. Scanning happens automatically.
            </p>
          )}
        </Card>

        {/* Manual Token Entry */}
        <Card className="mb-4">
          <div className="font-semibold mb-3">Manual Entry</div>
          <p className="text-xs text-gray-400 mb-3">
            If camera scanning is unavailable, paste the QR token below.
          </p>
          <div className="flex gap-2">
            <input
              value={token} onChange={e => setToken(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !loading && submitScan()}
              placeholder="Paste QR token here..."
              className="flex-1 min-w-0 px-3.5 py-2.5 border-[1.5px] border-gray-200 rounded text-[13px] font-mono outline-none focus:border-red"
            />
            <Button loading={loading || biometricLoading} onClick={() => submitScan()} disabled={dashboard?.markedToday || biometricLoading} className="animate-sign-pulse text-lg font-semibold !px-7 !py-4 !rounded-[12px] hover:scale-105 hover:shadow-[0_10px_24px_rgba(192,57,43,0.28)] active:scale-95">
              {biometricRegistered ? 'Verify & Sign Attendance' : 'Sign Attendance'}
            </Button>
          </div>
          {biometricRegistered && (
            <p className="text-[11px] text-gray-400 mt-2.5">
              Fingerprint verification will be required before marking attendance
            </p>
          )}
        </Card>

        <Card>
          <div className="font-semibold mb-3">Validation Checklist</div>
          {checks.map((c, i) => (
            <div key={i} className={`flex items-center gap-2.5 py-2 ${i < checks.length - 1 ? 'border-b border-gray-50' : ''}`}>
              <div className={`w-5 h-5 rounded-full shrink-0 flex items-center justify-center ${c.green || c.pass ? 'bg-green-light' : 'bg-red-light'}`}>
                <span className={`text-[10px] ${c.green || c.pass ? 'text-green-dark' : 'text-red'}`}>{c.pass ? '\u2713' : '\u2717'}</span>
              </div>
              <span className={`text-[13px] min-w-0 ${c.green ? 'font-semibold text-green-dark' : c.pass ? 'text-gray-900' : 'text-gray-400'}`}>
                {c.label}
                {c.optional && <span className="text-[10px] text-gray-300 ml-1.5">(recommended)</span>}
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
  const [page, setPage]       = useState(0)
  const [size, setSize]       = useState(20)
  const [total, setTotal]     = useState(0)
  const [totalPages, setTotalPages] = useState(1)

  useEffect(() => {
    setLoading(true)
    studentApi.historyPage({ page, size }).then(r => {
      setRecords(r.data.content || [])
      setTotal(r.data.totalElements || 0)
      setTotalPages(Math.max(r.data.totalPages || 1, 1))
    }).catch(() => toast.error('Failed to load history')).finally(() => setLoading(false))
  }, [page, size])

  if (loading) return <LoadingPage />

  const present = records.filter(r => r.status === 'PRESENT').length
  const late    = records.filter(r => r.status === 'LATE').length
  const absent  = records.filter(r => r.status === 'ABSENT').length
  const excused = records.filter(r => r.status === 'EXCUSED').length

  const cols = [
    { key: 'date',         label: 'Date',     strong: true, render: v => v ? format(new Date(v), 'dd MMM yyyy') : '—' },
    { key: 'markedAt',     label: 'Time',     render: v => v ? <span className="font-mono text-xs">{format(new Date(v), 'HH:mm')}</span> : '—' },
    { key: 'cohortName',   label: 'Cohort' },
    { key: 'status',       label: 'Status',   render: v => <Badge status={v} /> },
    { key: 'manual',       label: 'Type',     render: v => <Badge status={v ? 'MANUAL' : 'ACTIVE'} /> },
    { key: 'manualReason', label: 'Note',     render: v => v ? <span className="text-[11px] text-gray-400">{v}</span> : null },
  ]

  return (
    <>
      <PageHeader title="My Attendance" subtitle={`${total} records`} />
      <div className="p-4 sm:p-6 animate-fade-in">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
          <StatCard label="Present" value={present} badgeColor="green" />
          <StatCard label="Late"    value={late}    badgeColor="yellow" />
          <StatCard label="Absent"  value={absent}  badgeColor="red" />
          <StatCard label="Excused" value={excused} />
        </div>
        <Card className="overflow-x-auto">
          <Table columns={cols} rows={records} emptyMessage="No attendance records yet" />
          <Pagination page={page} totalPages={totalPages} totalElements={total} size={size} onChange={(p, s) => { setPage(p); if (s) setSize(s) }} />
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
      <div className="p-4 sm:p-6 max-w-[480px] animate-fade-in">
        {/* Biometric Settings */}
        <Card className="mb-4">
          <div className="font-semibold mb-1">Biometric Authentication</div>
          <p className="text-xs text-gray-400 mb-4">
            Register your fingerprint for secure attendance marking.
          </p>
          {platformAuth ? (
            biometricRegistered ? (
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-green-light flex items-center justify-center shrink-0">
                  <span className="text-xs text-green-dark">{'\u2713'}</span>
                </div>
                <span className="text-[13px] text-green-dark font-medium">Fingerprint registered</span>
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
          <div className="font-semibold mb-1">Change Password</div>
          <p className="text-xs text-gray-400 mb-5">Use a strong password of at least 6 characters.</p>
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
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card className="overflow-x-auto">
          <Table
            columns={[
              { key: 'startDate',    label: 'Start Date', render: v => v ? format(new Date(v), 'dd MMM yyyy') : '—' },
              { key: 'numberOfDays', label: 'Days',       render: v => `${v} day${v > 1 ? 's' : ''}` },
              { key: 'reason',       label: 'Reason',     strong: true },
              { key: 'coverUpPlan',  label: 'Cover-Up Plan', render: v => <span className="text-xs text-gray-600">{v}</span> },
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
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input label="Start Date *" type="date" value={form.startDate} onChange={e => setForm(p => ({ ...p, startDate: e.target.value }))} />
          <Input label="Number of Days *" type="number" min="1" max="30" value={form.numberOfDays} onChange={e => setForm(p => ({ ...p, numberOfDays: parseInt(e.target.value) || 1 }))} />
        </div>
        <Textarea label="Plan to Cover Missed Lessons *" value={form.coverUpPlan} onChange={e => setForm(p => ({ ...p, coverUpPlan: e.target.value }))} placeholder="Detail how you will study and submit assignments for missed classes..." rows={3} />
        <div className="flex justify-end gap-2 mt-4 flex-wrap">
          <Button variant="outline" onClick={() => setModal(false)}>Cancel</Button>
          <Button loading={saving} onClick={submit}>Submit Request</Button>
        </div>
      </Modal>
    </>
  )
}
