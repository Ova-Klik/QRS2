import React, { useState, useEffect, useCallback, useRef } from 'react'
import { publicApi } from '../../api/client'
import { useSchool } from '../../context/SchoolContext'
import toast from 'react-hot-toast'
import { useTheme } from '../../context/ThemeContext'

export function ProjectionPage() {
  const { settings } = useSchool()
  const { dark, toggle } = useTheme()
  const C = dark ? {
    bg:            'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
    fg:            '#f8fafc',
    muted:         '#94a3b8',
    dim:           '#64748b',
    soft:          '#cbd5e1',
    border:        'rgba(255,255,255,0.1)',
    borderSoft:    'rgba(255,255,255,0.05)',
    borderStrong:  'rgba(255,255,255,0.12)',
    cardBg:        'rgba(30, 41, 59, 0.7)',
    cardBgDeep:    'rgba(15, 23, 42, 0.5)',
    selectBg:      '#334155',
    selectBorder:  '#475569',
    controlBg:     'rgba(255,255,255,0.1)',
    controlBorder: 'rgba(255,255,255,0.2)',
    linkBg:        'rgba(0,0,0,0.2)',
    progressBg:    '#334155',
    shadow:        '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
    green:         '#4ade80',
    amber:         '#fbbf24',
  } : {
    bg:            'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
    fg:            '#0f172a',
    muted:         '#64748b',
    dim:           '#94a3b8',
    soft:          '#475569',
    border:        'rgba(15,23,42,0.1)',
    borderSoft:    'rgba(15,23,42,0.06)',
    borderStrong:  'rgba(15,23,42,0.12)',
    cardBg:        'rgba(255,255,255,0.78)',
    cardBgDeep:    'rgba(255,255,255,0.6)',
    selectBg:      '#fff',
    selectBorder:  '#cbd5e1',
    controlBg:     'rgba(15,23,42,0.08)',
    controlBorder: 'rgba(15,23,42,0.15)',
    linkBg:        'rgba(255,255,255,0.6)',
    progressBg:    '#e2e8f0',
    shadow:        '0 25px 50px -12px rgba(15, 23, 42, 0.18)',
    green:         '#16a34a',
    amber:         '#d97706',
  }
  const [cohorts, setCohorts]       = useState([])
  const [selectedCohort, setSelected] = useState('')
  const [session, setSession]       = useState(null)
  const [summary, setSummary]       = useState(null)
  const [loading, setLoading]       = useState(true)
  const [totpCountdown, setTotpCountdown] = useState(10)
  const [liveRemaining, setLiveRemaining] = useState(0)
  const [isMobile, setIsMobile]     = useState(false)
  const expiresAtRef = useRef(null)

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 768)
    onResize()
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  // Load cohorts on mount
  useEffect(() => {
    publicApi.listCohorts()
      .then(r => {
        setCohorts(r.data)
        if (r.data.length > 0) {
          setSelected(r.data[0].id)
        }
      })
      .catch(() => toast.error('Failed to load cohorts'))
      .finally(() => setLoading(false))
  }, [])

  // Fetch QR session & summary for selected cohort
  const fetchSession = useCallback((cohortId) => {
    if (!cohortId) return
    publicApi.getQrSession(cohortId, window.location.origin)
      .then(r => {
        setSession(r.data)
        if (r.data?.expiresAt) {
          const rem = Math.max(0, Math.floor((new Date(r.data.expiresAt).getTime() - Date.now()) / 1000))
          setLiveRemaining(rem)
          expiresAtRef.current = r.data.expiresAt
        } else {
          setLiveRemaining(0)
        }
      })
      .catch(() => { setSession(null); setLiveRemaining(0) })

    publicApi.getTodaySummary(cohortId)
      .then(r => setSummary(r.data))
      .catch(() => setSummary(null))
  }, [])

  // Live seconds countdown ticker
  useEffect(() => {
    if (!session || session.state !== 'ACTIVE') {
      setLiveRemaining(0)
      return
    }
    const tick = () => {
      if (expiresAtRef.current) {
        const rem = Math.max(0, Math.floor((new Date(expiresAtRef.current).getTime() - Date.now()) / 1000))
        setLiveRemaining(rem)
        if (rem <= 0) {
          setSession(prev => prev ? { ...prev, state: 'EXPIRED' } : null)
        }
      }
    }
    tick()
    const interval = setInterval(tick, 1000)
    return () => clearInterval(interval)
  }, [session?.sessionId, session?.state])

  // Initial load when cohort changes
  useEffect(() => {
    if (selectedCohort) {
      fetchSession(selectedCohort)
    }
  }, [selectedCohort, fetchSession])

  // 10-second rolling TOTP refresher interval
  useEffect(() => {
    if (!selectedCohort) return
    const interval = setInterval(() => {
      fetchSession(selectedCohort)
      setTotpCountdown(10)
    }, 10000)

    const timer = setInterval(() => {
      setTotpCountdown(prev => (prev > 1 ? prev - 1 : 10))
    }, 1000)

    return () => {
      clearInterval(interval)
      clearInterval(timer)
    }
  }, [selectedCohort, fetchSession])

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {})
    } else {
      if (document.exitFullscreen) document.exitFullscreen()
    }
  }

  const remainingSeconds = liveRemaining
  const isExpired = session?.state === 'EXPIRED' || remainingSeconds <= 0
  const mins = Math.floor(remainingSeconds / 60)
  const secs = remainingSeconds % 60

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', background: dark ? '#0f172a' : '#f1f5f9', color: C.fg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ fontSize: 18, color: C.muted }}>Loading classroom display...</div>
      </div>
    )
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: C.bg,
      color: C.fg,
      fontFamily: 'Inter, system-ui, sans-serif',
      display: 'flex',
      flexDirection: 'column',
      padding: isMobile ? '12px 16px' : '24px 36px',
      boxSizing: 'border-box'
    }}>
      {/* Top Bar */}
      <div style={{ display: 'flex', flexDirection: isMobile ? 'column' : 'row', alignItems: isMobile ? 'stretch' : 'center', justifyContent: 'space-between', marginBottom: 16, borderBottom: `1px solid ${C.border}`, paddingBottom: 12, gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ background: '#ef4444', color: '#fff', fontWeight: 800, padding: '6px 12px', borderRadius: 10, fontSize: 14, letterSpacing: '.05em', flexShrink: 0 }}>
            QRS
          </div>
          <div>
            <h1 style={{ fontSize: isMobile ? 16 : 20, fontWeight: 700, margin: 0, color: C.fg }}>Classroom Projection</h1>
            <p style={{ fontSize: 11, color: C.muted, margin: 0 }}>{settings?.school_name || 'Tech School'} — Scan dynamic QR code</p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <select
            value={selectedCohort}
            onChange={e => setSelected(e.target.value)}
            style={{
              background: C.selectBg, color: C.fg, border: `1px solid ${C.selectBorder}`,
              padding: '8px 12px', borderRadius: 8, fontSize: 13, fontWeight: 600, outline: 'none', cursor: 'pointer', flex: isMobile ? 1 : 'none',
            }}
          >
            {cohorts.map(c => (
              <option key={c.id} value={c.id}>{c.name} ({c.studentCount || 0} students)</option>
            ))}
          </select>

          <button
            onClick={toggle}
            title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            style={{
              background: C.controlBg, color: C.fg, border: `1px solid ${C.controlBorder}`,
              padding: '8px 12px', borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: 'pointer', transition: 'all .15s'
            }}
          >
            {dark ? (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ verticalAlign: -2, marginRight: 5 }}><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
            ) : (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ verticalAlign: -2, marginRight: 5 }}><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
            )}
          </button>

          <button
            onClick={toggleFullscreen}
            style={{
              background: C.controlBg, color: C.fg, border: `1px solid ${C.controlBorder}`,
              padding: '8px 12px', borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: 'pointer', transition: 'all .15s'
            }}
          >
            ⛶ Fullscreen
          </button>

          <a
            href="https://qrsattendance.netlify.app"
            style={{
              color: C.muted, fontSize: 12, textDecoration: 'none', padding: '8px 12px', borderRadius: 8,
              border: `1px solid ${C.border}`, background: C.linkBg
            }}
          >
            ← Back to Login
          </a>
        </div>
      </div>

      {/* Main Content Display */}
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '2.5fr 1fr', gap: 20, alignItems: 'center', justifyItems: 'center' }}>
        {/* Center Projection Card */}
        <div style={{
          background: C.cardBg,
          backdropFilter: 'blur(16px)',
          border: `1px solid ${C.borderStrong}`,
          borderRadius: 24,
          padding: isMobile ? 24 : 40,
          textAlign: 'center',
          boxShadow: C.shadow,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: isMobile ? 320 : 480
        }}>
          {session && !isExpired ? (
            <>
              <div style={{ marginBottom: 12, display: 'inline-flex', alignItems: 'center', gap: 8, background: 'rgba(34, 197, 94, 0.15)', border: '1px solid rgba(34, 197, 94, 0.4)', color: '#4ade80', padding: '6px 16px', borderRadius: 20, fontSize: 13, fontWeight: 600 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#22c55e', display: 'inline-block', boxShadow: '0 0 10px #22c55e' }} />
                Active Session — 10s TOTP Rotation Live
              </div>

              {/* QR Image Frame */}
              <div style={{
                position: 'relative',
                background: '#fff',
                padding: 16,
                borderRadius: 20,
                boxShadow: '0 0 40px rgba(239, 68, 68, 0.25)',
                marginBottom: 20,
                marginTop: 8
              }}>
                <img
                  src={`data:image/png;base64,${session.qrImageBase64}`}
                  alt="Live Session QR"
                  style={{ width: isMobile ? 180 : 260, height: isMobile ? 180 : 260, display: 'block', borderRadius: 8 }}
                />
              </div>

              {/* TOTP Progress Bar */}
              <div style={{ width: 280, marginBottom: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: C.muted, marginBottom: 4 }}>
                  <span>Dynamic Code Security Refresh</span>
                  <span style={{ fontFamily: 'monospace' }}>{totpCountdown}s</span>
                </div>
                <div style={{ height: 4, background: C.progressBg, borderRadius: 2, overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${(totpCountdown / 10) * 100}%`, background: '#3b82f6', transition: 'width 1s linear' }} />
                </div>
              </div>

              <div style={{ fontSize: 14, color: C.soft }}>
                Cohort: <strong style={{ color: C.fg }}>{(session.cohortName || '').match(/\d+/)?.[0] || session.cohortName}</strong>
              </div>
            </>
          ) : (
            <div style={{ padding: '60px 20px', color: C.muted }}>
              <div style={{ fontSize: 64, marginBottom: 16 }}>⛔</div>
              <h2 style={{ fontSize: 22, color: C.fg, marginBottom: 8 }}>Session Expired or Inactive</h2>
              <p style={{ fontSize: 14, maxWidth: 360, margin: '0 auto 20px auto' }}>
                The automated QR attendance session has ended. Request facilitator to generate a new session or set session duration.
              </p>
              <button
                onClick={() => fetchSession(selectedCohort)}
                style={{
                  background: '#ef4444', color: '#fff', border: 'none', padding: '10px 20px',
                  borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer'
                }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> Refresh Session
              </button>
            </div>
          )}
        </div>

        {/* Sidebar Info Panel */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Countdown Card */}
          <div style={{ background: C.cardBg, border: `1px solid ${C.border}`, borderRadius: 20, padding: 24 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: C.muted, textTransform: 'uppercase', letterSpacing: '.05em', marginBottom: 8 }}>
              Remaining Session Duration
            </div>
            <div style={{ fontFamily: 'monospace', fontSize: 36, fontWeight: 700, color: isExpired ? '#ef4444' : C.fg }}>
              {String(mins).padStart(2, '0')}:{String(secs).padStart(2, '0')}
            </div>
            <div style={{ fontSize: 11, color: C.dim, marginTop: 4 }}>
              Session auto-stops when countdown reaches 00:00
            </div>
          </div>

          {/* Live Attendance Counter Card */}
          <div style={{ background: C.cardBg, border: `1px solid ${C.border}`, borderRadius: 20, padding: 24 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: C.muted, textTransform: 'uppercase', letterSpacing: '.05em', marginBottom: 12 }}>
              Live Attendance Count
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div style={{ background: 'rgba(34, 197, 94, 0.1)', border: '1px solid rgba(34, 197, 94, 0.2)', padding: 12, borderRadius: 12, textAlign: 'center' }}>
                <div style={{ fontSize: 24, fontWeight: 700, color: C.green }}>
                  {summary ? (summary.present + summary.late) : 0}
                </div>
                <div style={{ fontSize: 11, color: C.muted, marginTop: 2 }}>Scanned / Present</div>
              </div>

              <div style={{ background: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.2)', padding: 12, borderRadius: 12, textAlign: 'center' }}>
                <div style={{ fontSize: 24, fontWeight: 700, color: C.amber }}>
                  {summary ? summary.late : 0}
                </div>
                <div style={{ fontSize: 11, color: C.muted, marginTop: 2 }}>Late Arrivals</div>
              </div>
            </div>
          </div>

          {/* Quick Instructions */}
          <div style={{ background: C.cardBgDeep, border: `1px solid ${C.borderSoft}`, borderRadius: 20, padding: 20 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: C.soft, marginBottom: 8 }}>📲 Student Instructions:</div>
            <ol style={{ paddingLeft: 18, margin: 0, fontSize: 12, color: C.muted, lineHeight: 1.6 }}>
              <li>Connect to school WiFi or allow GPS location.</li>
              <li>Log into student account on phone.</li>
              <li>Scan the projected QR code above before 8:30 AM for On-Time status.</li>
            </ol>
          </div>
        </div>
      </div>
    </div>
  )
}
