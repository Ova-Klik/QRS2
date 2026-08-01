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
      <div className="min-h-screen flex items-center justify-center" style={{ background: dark ? '#0f172a' : '#f1f5f9', color: C.fg }}>
        <div className="text-lg" style={{ color: C.muted }}>Loading classroom display...</div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex flex-col px-4 py-3 sm:px-9 sm:py-6" style={{ background: C.bg, color: C.fg, fontFamily: 'Inter, system-ui, sans-serif' }}>
      {/* Top Bar */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between mb-4 border-b pb-3 gap-3" style={{ borderColor: C.border }}>
        <div className="flex items-center gap-3">
          <div className="bg-[#ef4444] text-white font-extrabold px-3 py-1.5 rounded-lg text-sm tracking-wide shrink-0">
            QRS
          </div>
          <div className="min-w-0">
            <h1 className="text-base md:text-xl font-bold m-0" style={{ color: C.fg }}>Classroom Projection</h1>
            <p className="text-[11px] m-0" style={{ color: C.muted }}>{settings?.school_name || 'Tech School'} — Scan dynamic QR code</p>
          </div>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <select
            value={selectedCohort}
            onChange={e => setSelected(e.target.value)}
            className="px-3 py-2 rounded-md text-[13px] font-semibold outline-none cursor-pointer flex-1 md:flex-none"
            style={{ background: C.selectBg, color: C.fg, border: `1px solid ${C.selectBorder}` }}
          >
            {cohorts.map(c => (
              <option key={c.id} value={c.id}>{c.name} ({c.studentCount || 0} students)</option>
            ))}
          </select>

          <button
            onClick={toggle}
            title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            className="px-3 py-2 rounded-md text-xs font-semibold cursor-pointer transition-all duration-150"
            style={{ background: C.controlBg, color: C.fg, border: `1px solid ${C.controlBorder}` }}
          >
            {dark ? (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ verticalAlign: -2, marginRight: 5 }}><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
            ) : (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ verticalAlign: -2, marginRight: 5 }}><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
            )}
          </button>

          <button
            onClick={toggleFullscreen}
            className="px-3 py-2 rounded-md text-xs font-semibold cursor-pointer transition-all duration-150"
            style={{ background: C.controlBg, color: C.fg, border: `1px solid ${C.controlBorder}` }}
          >
            ⛶ Fullscreen
          </button>

          <a
            href="https://qrsattendance.netlify.app"
            className="text-xs no-underline px-3 py-2 rounded-md"
            style={{ color: C.muted, border: `1px solid ${C.border}`, background: C.linkBg }}
          >
            ← Back to Login
          </a>
        </div>
      </div>

      {/* Main Content Display */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-[2.5fr_1fr] gap-5 items-center justify-items-center">
        {/* Center Projection Card */}
        <div className="flex flex-col items-center justify-center text-center rounded-3xl p-6 sm:p-10 min-h-[320px] lg:min-h-[480px]"
          style={{ background: C.cardBg, backdropFilter: 'blur(16px)', border: `1px solid ${C.borderStrong}`, boxShadow: C.shadow }}>
          {session && !isExpired ? (
            <>
              <div className="mb-3 inline-flex items-center gap-2 bg-[rgba(34,197,94,0.15)] border border-[rgba(34,197,94,0.4)] text-[#4ade80] px-4 py-1.5 rounded-full text-[13px] font-semibold">
                <span className="w-2 h-2 rounded-full bg-[#22c55e] inline-block shadow-[0_0_10px_#22c55e]" />
                Active Session — 10s TOTP Rotation Live
              </div>

              {/* QR Image Frame */}
              <div className="relative bg-white p-4 rounded-[20px] shadow-[0_0_40px_rgba(239,68,68,0.25)] mb-5 mt-2">
                <img
                  src={`data:image/png;base64,${session.qrImageBase64}`}
                  alt="Live Session QR"
                  className="block rounded"
                  style={{ width: isMobile ? 180 : 260, height: isMobile ? 180 : 260 }}
                />
              </div>

              {/* TOTP Progress Bar */}
              <div className="w-[280px] max-w-full mb-4">
                <div className="flex justify-between text-[11px] mb-1" style={{ color: C.muted }}>
                  <span>Dynamic Code Security Refresh</span>
                  <span className="font-mono">{totpCountdown}s</span>
                </div>
                <div className="h-1 rounded-[2px] overflow-hidden" style={{ background: C.progressBg }}>
                  <div className="h-full bg-[#3b82f6] transition-[width] duration-1000 ease-linear" style={{ width: `${(totpCountdown / 10) * 100}%` }} />
                </div>
              </div>

              <div className="text-sm" style={{ color: C.soft }}>
                Cohort: <strong style={{ color: C.fg }}>{(session.cohortName || '').match(/\d+/)?.[0] || session.cohortName}</strong>
              </div>
            </>
          ) : (
            <div className="px-5 py-14" style={{ color: C.muted }}>
              <div className="text-[64px] mb-4">⛔</div>
              <h2 className="text-2xl sm:text-4xl mb-2" style={{ color: C.fg }}>Session Expired or Inactive</h2>
              <p className="text-sm max-w-[360px] mx-auto mb-5">
                The automated QR attendance session has ended. Request facilitator to generate a new session or set session duration.
              </p>
              <button
                onClick={() => fetchSession(selectedCohort)}
                className="bg-[#ef4444] text-white border-0 px-5 py-2.5 rounded-md text-sm font-semibold cursor-pointer"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> Refresh Session
              </button>
            </div>
          )}
        </div>

        {/* Sidebar Info Panel */}
        <div className="flex flex-col gap-5 w-full max-w-[380px]">
          {/* Countdown Card */}
          <div className="rounded-[20px] p-6" style={{ background: C.cardBg, border: `1px solid ${C.border}` }}>
            <div className="text-xs font-semibold uppercase tracking-wide mb-2" style={{ color: C.muted }}>
              Remaining Session Duration
            </div>
            <div className="font-mono text-4xl font-bold" style={{ color: isExpired ? '#ef4444' : C.fg }}>
              {String(mins).padStart(2, '0')}:{String(secs).padStart(2, '0')}
            </div>
            <div className="text-[11px] mt-1" style={{ color: C.dim }}>
              Session auto-stops when countdown reaches 00:00
            </div>
          </div>

          {/* Live Attendance Counter Card */}
          <div className="rounded-[20px] p-6" style={{ background: C.cardBg, border: `1px solid ${C.border}` }}>
            <div className="text-xs font-semibold uppercase tracking-wide mb-3" style={{ color: C.muted }}>
              Live Attendance Count
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-[rgba(34,197,94,0.1)] border border-[rgba(34,197,94,0.2)] p-3 rounded-xl text-center">
                <div className="text-2xl font-bold" style={{ color: C.green }}>
                  {summary ? (summary.present + summary.late) : 0}
                </div>
                <div className="text-[11px] mt-0.5" style={{ color: C.muted }}>Scanned / Present</div>
              </div>

              <div className="bg-[rgba(245,158,11,0.1)] border border-[rgba(245,158,11,0.2)] p-3 rounded-xl text-center">
                <div className="text-2xl font-bold" style={{ color: C.amber }}>
                  {summary ? summary.late : 0}
                </div>
                <div className="text-[11px] mt-0.5" style={{ color: C.muted }}>Late Arrivals</div>
              </div>
            </div>
          </div>

          {/* Quick Instructions */}
          <div className="rounded-[20px] p-5" style={{ background: C.cardBgDeep, border: `1px solid ${C.borderSoft}` }}>
            <div className="text-xs font-semibold mb-2" style={{ color: C.soft }}>📲 Student Instructions:</div>
            <ol className="pl-[18px] m-0 text-xs leading-relaxed" style={{ color: C.muted }}>
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
