import React, { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { publicApi } from '../../api/client'
import { useSchool } from '../../context/SchoolContext'
import toast from 'react-hot-toast'

export function ProjectionPage() {
  const { settings } = useSchool()
  const [cohorts, setCohorts]       = useState([])
  const [selectedCohort, setSelected] = useState('')
  const [session, setSession]       = useState(null)
  const [summary, setSummary]       = useState(null)
  const [loading, setLoading]       = useState(true)
  const [totpCountdown, setTotpCountdown] = useState(10)
  const [liveRemaining, setLiveRemaining] = useState(0)

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
    publicApi.getQrSession(cohortId)
      .then(r => {
        setSession(r.data)
        if (r.data?.expiresAt) {
          const rem = Math.max(0, Math.floor((new Date(r.data.expiresAt).getTime() - Date.now()) / 1000))
          setLiveRemaining(rem)
        }
      })
      .catch(() => setSession(null))

    publicApi.getTodaySummary(cohortId)
      .then(r => setSummary(r.data))
      .catch(() => setSummary(null))
  }, [])

  // Live seconds countdown ticker
  useEffect(() => {
    if (!session || session.state === 'EXPIRED') {
      setLiveRemaining(0)
      return
    }
    const tick = () => {
      if (session.expiresAt) {
        const rem = Math.max(0, Math.floor((new Date(session.expiresAt).getTime() - Date.now()) / 1000))
        setLiveRemaining(rem)
        if (rem === 0 && session.state === 'ACTIVE') {
          setSession(prev => prev ? { ...prev, state: 'EXPIRED' } : null)
        }
      }
    }
    tick()
    const interval = setInterval(tick, 1000)
    return () => clearInterval(interval)
  }, [session])

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
      <div style={{ minHeight: '100vh', background: '#0f172a', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ fontSize: 18, color: '#94a3b8' }}>Loading classroom display...</div>
      </div>
    )
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
      color: '#f8fafc',
      fontFamily: 'Inter, system-ui, sans-serif',
      display: 'flex',
      flexDirection: 'column',
      padding: '24px 36px',
      boxSizing: 'border-box'
    }}>
      {/* Top Bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24, borderBottom: '1px solid rgba(255,255,255,0.1)', pb: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div style={{ background: '#ef4444', color: '#fff', fontWeight: 800, padding: '8px 16px', borderRadius: 10, fontSize: 16, letterSpacing: '.05em' }}>
            QRS
          </div>
          <div>
            <h1 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#fff' }}>Classroom Projection Screen</h1>
            <p style={{ fontSize: 12, color: '#94a3b8', margin: 0 }}>{settings?.school_name || 'Tech School'} — Scan dynamic QR code before activity starts</p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <select
            value={selectedCohort}
            onChange={e => setSelected(e.target.value)}
            style={{
              background: '#334155', color: '#fff', border: '1px solid #475569',
              padding: '10px 16px', borderRadius: 8, fontSize: 14, fontWeight: 600, outline: 'none', cursor: 'pointer'
            }}
          >
            {cohorts.map(c => (
              <option key={c.id} value={c.id}>{c.name} ({c.studentCount || 0} students)</option>
            ))}
          </select>

          <button
            onClick={toggleFullscreen}
            style={{
              background: 'rgba(255,255,255,0.1)', color: '#fff', border: '1px solid rgba(255,255,255,0.2)',
              padding: '10px 16px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer', transition: 'all .15s'
            }}
          >
            ⛶ Fullscreen
          </button>

          <Link
            to="/login"
            style={{
              color: '#94a3b8', fontSize: 13, textDecoration: 'none', padding: '10px 14px', borderRadius: 8,
              border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.2)'
            }}
          >
            ← Back to Login
          </Link>
        </div>
      </div>

      {/* Main Content Display */}
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 340px', gap: 32, alignItems: 'center' }}>
        {/* Center Projection Card */}
        <div style={{
          background: 'rgba(30, 41, 59, 0.7)',
          backdropFilter: 'blur(16px)',
          border: '1px solid rgba(255, 255, 255, 0.12)',
          borderRadius: 24,
          padding: 40,
          textAlign: 'center',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: 480
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
                  style={{ width: 260, height: 260, display: 'block', borderRadius: 8 }}
                />
              </div>

              {/* TOTP Progress Bar */}
              <div style={{ width: 280, marginBottom: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#94a3b8', marginBottom: 4 }}>
                  <span>Dynamic Code Security Refresh</span>
                  <span style={{ fontFamily: 'monospace' }}>{totpCountdown}s</span>
                </div>
                <div style={{ height: 4, background: '#334155', borderRadius: 2, overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${(totpCountdown / 10) * 100}%`, background: '#3b82f6', transition: 'width 1s linear' }} />
                </div>
              </div>

              <div style={{ fontSize: 14, color: '#cbd5e1' }}>
                Cohort: <strong style={{ color: '#fff' }}>{session.cohortName}</strong>
              </div>
            </>
          ) : (
            <div style={{ padding: '60px 20px', color: '#94a3b8' }}>
              <div style={{ fontSize: 64, marginBottom: 16 }}>⛔</div>
              <h2 style={{ fontSize: 22, color: '#f8fafc', marginBottom: 8 }}>Session Expired or Inactive</h2>
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
          <div style={{ background: 'rgba(30, 41, 59, 0.7)', border: '1px solid rgba(255, 255, 255, 0.1)', borderRadius: 20, padding: 24 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.05em', marginBottom: 8 }}>
              Remaining Session Duration
            </div>
            <div style={{ fontFamily: 'monospace', fontSize: 36, fontWeight: 700, color: isExpired ? '#ef4444' : '#f8fafc' }}>
              {String(mins).padStart(2, '0')}:{String(secs).padStart(2, '0')}
            </div>
            <div style={{ fontSize: 11, color: '#64748b', marginTop: 4 }}>
              Session auto-stops when countdown reaches 00:00
            </div>
          </div>

          {/* Live Attendance Counter Card */}
          <div style={{ background: 'rgba(30, 41, 59, 0.7)', border: '1px solid rgba(255, 255, 255, 0.1)', borderRadius: 20, padding: 24 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.05em', marginBottom: 12 }}>
              Live Attendance Count
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div style={{ background: 'rgba(34, 197, 94, 0.1)', border: '1px solid rgba(34, 197, 94, 0.2)', padding: 12, borderRadius: 12, textAlign: 'center' }}>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#4ade80' }}>
                  {summary ? (summary.present + summary.late) : 0}
                </div>
                <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>Scanned / Present</div>
              </div>

              <div style={{ background: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.2)', padding: 12, borderRadius: 12, textAlign: 'center' }}>
                <div style={{ fontSize: 24, fontWeight: 700, color: '#fbbf24' }}>
                  {summary ? summary.late : 0}
                </div>
                <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>Late Arrivals</div>
              </div>
            </div>
          </div>

          {/* Quick Instructions */}
          <div style={{ background: 'rgba(15, 23, 42, 0.5)', border: '1px solid rgba(255, 255, 255, 0.05)', borderRadius: 20, padding: 20 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#cbd5e1', marginBottom: 8 }}>📲 Student Instructions:</div>
            <ol style={{ paddingLeft: 18, margin: 0, fontSize: 12, color: '#94a3b8', lineHeight: 1.6 }}>
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
