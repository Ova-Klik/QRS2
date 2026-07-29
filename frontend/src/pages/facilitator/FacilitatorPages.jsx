import React, { useState, useEffect, useCallback } from 'react'
import { facilitatorApi, adminApi } from '../../api/client'
import { Card, StatCard, Badge, Table, PageHeader, LoadingPage, Alert, Button, Select, Textarea, Modal } from '../../components/common/UI'
import { useSchool } from '../../context/SchoolContext'
import toast from 'react-hot-toast'
import { format } from 'date-fns'

// ── Dashboard ────────────────────────────────────────────
export function FacilitatorDashboard() {
  const [data, setData]     = useState(null)
  const [loading, setLoading] = useState(true)
  const reload = useCallback(() => {
    facilitatorApi.dashboard().then(r => setData(r.data)).catch(() => toast.error('Failed to load')).finally(() => setLoading(false))
  }, [])
  useEffect(() => { reload() }, [reload])

  if (loading) return <LoadingPage />
  const d = data || {}

  return (
    <>
      <PageHeader title="Facilitator Dashboard" subtitle={format(new Date(), "EEEE, dd MMM yyyy")}
        actions={<Button variant="outline" size="sm" onClick={reload}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> Refresh</Button>} />
      <div style={{ padding: 24 }} className="fade-in">
        {d.qrSessionActive
          ? <Alert type="success"><strong>QR Session is active</strong> — Students can scan until the session expires.</Alert>
          : <Alert type="warning"><strong>No active QR session.</strong> Go to QR Generator to open today's session.</Alert>
        }
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12, marginBottom: 20 }}>
          <StatCard label="Total Students" value={d.totalStudents || 0} />
          <StatCard label="Present"  value={d.presentToday || 0} badgeColor="green" badge="On time" />
          <StatCard label="Late"     value={d.lateToday    || 0} badgeColor="yellow" badge="After 7:30" />
          <StatCard label="Absent"   value={d.absentToday  || 0} badgeColor="red" badge="No record" color={d.absentToday > 0 ? 'var(--red)' : undefined} />
        </div>
        <Card>
          <div style={{ fontWeight: 600, marginBottom: 16 }}>Today's Live Attendance</div>
          <Table
            columns={[
              { key: 'studentName', label: 'Student',  strong: true },
              { key: 'cohortName',  label: 'Cohort' },
              { key: 'status',      label: 'Status',  render: v => <Badge status={v} /> },
              { key: 'markedAt',    label: 'Time',    render: v => v ? <span style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>{format(new Date(v), 'HH:mm')}</span> : '—' },
              { key: 'manual',      label: 'Type',    render: v => <Badge status={v ? 'MANUAL' : 'ACTIVE'} /> },
            ]}
            rows={d.todayRecords || []}
            emptyMessage="No attendance records yet today"
          />
        </Card>
      </div>
    </>
  )
}

// ── QR Generator ─────────────────────────────────────────
export function FacilitatorQR() {
  const { settings } = useSchool()
  const [cohorts, setCohorts]       = useState([])
  const [selectedCohort, setSelected] = useState('')
  const [durationMinutes, setDuration] = useState('')
  const [session, setSession]       = useState(null)
  const [generating, setGenerating] = useState(false)
  const [remaining, setRemaining]   = useState(0)
  const [windowRemaining, setWindowRemaining] = useState(0)

  useEffect(() => {
    facilitatorApi.myCohorts().then(r => {
      setCohorts(r.data)
      if (r.data.length > 0) setSelected(r.data[0].id)
    })
  }, [])

  const qrWindowEnd = settings?.qr_window_end || '12:00'

  useEffect(() => {
    if (!selectedCohort) return
    facilitatorApi.getActiveQr(selectedCohort)
      .then(r => setSession(r.data))
      .catch(() => setSession(null))
  }, [selectedCohort])

  // Timer for active session countdown
  useEffect(() => {
    if (!session) return
    const tick = () => {
      const rem = Math.max(0, Math.floor((new Date(session.expiresAt) - Date.now()) / 1000))
      setRemaining(rem)
      if (rem === 0) setSession(prev => prev ? { ...prev, state: 'EXPIRED' } : null)
    }
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [session])

  // Timer for daily window countdown (always runs when no active session)
  useEffect(() => {
    const tick = () => {
      const now = new Date()
      const [h, m] = qrWindowEnd.split(':').map(Number)
      const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), h, m, 0)
      const rem = Math.max(0, Math.floor((end - now) / 1000))
      setWindowRemaining(rem)
    }
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [qrWindowEnd])

  const generate = async () => {
    if (!selectedCohort) { toast.error('Select a cohort'); return }
    setGenerating(true)
    try {
      const parsedDuration = durationMinutes ? parseInt(durationMinutes) : null
      const { data } = await facilitatorApi.generateQr(selectedCohort, parsedDuration)
      setSession(data)
      toast.success('QR session generated and active!')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to generate QR')
    } finally {
      setGenerating(false)
    }
  }

  const expire = async () => {
    if (!session) return
    try {
      await facilitatorApi.expireQr(session.sessionId)
      setSession(prev => ({ ...prev, state: 'EXPIRED' }))
      toast.success('QR session stopped')
    } catch { toast.error('Failed to stop QR session') }
  }

  const isActive = session?.state === 'ACTIVE' && remaining > 0
  const displayRemaining = isActive ? remaining : windowRemaining
  const mins = Math.floor(displayRemaining / 60), secs = displayRemaining % 60
  const timerLabel = isActive ? 'Session Remaining' : 'Window Closes In'

  return (
    <>
      <PageHeader title="QR Generator" subtitle="Generate or stop daily attendance QR codes with custom session durations" />
      <div style={{ padding: 24 }} className="fade-in">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, maxWidth: 800 }}>
            {/* Controls */}
          <Card>
            <div style={{ fontWeight: 600, marginBottom: 16 }}>Manage QR Session</div>
            <Select label="Cohort" value={selectedCohort} onChange={e => setSelected(e.target.value)}>
              {cohorts.map(c => <option key={c.id} value={c.id}>{c.name} ({c.studentCount || 0} students)</option>)}
            </Select>
            <Select label="Session Auto-Stop Duration" value={durationMinutes} onChange={e => setDuration(e.target.value)}>
              <option value="">Default Window (07:00 – {qrWindowEnd})</option>
              <option value="15">15 Minutes</option>
              <option value="30">30 Minutes</option>
              <option value="45">45 Minutes</option>
              <option value="60">60 Minutes (1 Hour)</option>
              <option value="120">120 Minutes (2 Hours)</option>
              <option value="180">180 Minutes (3 Hours)</option>
            </Select>
            <div style={{ background: 'var(--off)', borderRadius: 8, padding: 12, marginBottom: 16 }}>
              <div style={{ fontSize: 11, color: 'var(--gray-400)', marginBottom: 4 }}>SESSION STATUS</div>
              <div style={{ fontSize: 13, fontWeight: 500, color: isActive ? 'var(--green-dark)' : 'var(--red)', display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: isActive ? 'var(--green)' : 'var(--red)', display: 'inline-block' }} />
                {isActive ? 'Active Session Available' : 'No Active Session'}
              </div>
              <div style={{ fontSize: 12, color: 'var(--gray-600)', marginTop: 2 }}>
                {isActive ? '10-second dynamic TOTP rolling rotation enabled' : 'Select cohort & duration to generate session'}
              </div>
            </div>
            <Button onClick={generate} loading={generating} style={{ width: '100%', justifyContent: 'center' }}>
              {isActive ? (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
              ) : (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="5 3 19 12 5 21 5 3"/></svg>
              )}
              {isActive ? 'Generate New QR Session' : 'Generate QR Code'}
            </Button>
            {isActive && (
              <Button variant="outline" onClick={expire} style={{ width: '100%', justifyContent: 'center', marginTop: 8, color: 'var(--red)', borderColor: 'var(--red-mid)' }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="4" y="4" width="16" height="16" rx="2"/><line x1="9" y1="9" x2="15" y2="15"/><line x1="15" y1="9" x2="9" y2="15"/></svg>
                Stop QR Session
              </Button>
            )}
          </Card>

          {/* QR Display */}
          <Card style={{ textAlign: 'center' }}>
            <div style={{ fontWeight: 600, marginBottom: 16 }}>Session QR</div>
            {session ? (
              <>
                <div style={{ position: 'relative', display: 'inline-block', marginBottom: 12 }}>
                  <img src={`data:image/png;base64,${session.qrImageBase64}`} alt="QR Code" style={{ width: 180, height: 180, borderRadius: 8, opacity: isActive ? 1 : .3 }} />
                  {!isActive && (
                    <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: 'rgba(255,255,255,.85)', borderRadius: 8 }}>
                      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--red)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                      <span style={{ fontSize: 13, color: 'var(--red)', fontWeight: 500, marginTop: 4 }}>Expired</span>
                    </div>
                  )}
                </div>
                <div style={{ fontSize: 11, color: 'var(--gray-400)', marginBottom: 2 }}>{timerLabel}</div>
                <div style={{ fontFamily: 'var(--mono)', fontSize: 28, fontWeight: 500, color: displayRemaining < 300 ? 'var(--red)' : 'var(--gray-900)', marginBottom: 4 }}>
                  {String(mins).padStart(2, '0')}:{String(secs).padStart(2, '0')}
                </div>
                <div style={{ marginBottom: 8, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 12px', borderRadius: 20, fontSize: 12, fontWeight: 500, background: isActive ? 'var(--green-light)' : 'var(--yellow-light)', color: isActive ? 'var(--green-dark)' : 'var(--yellow-dark)', border: `1px solid ${isActive ? '#a8dbb8' : '#f3dfa8'}` }}>
                    {isActive && <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--green)', animation: 'pulse 1.5s infinite', display: 'inline-block' }} />}
                    {isActive ? 'Active (10s TOTP)' : `Window ends at ${qrWindowEnd}`}
                  </span>
                </div>
                <div style={{ fontSize: 11, color: 'var(--gray-400)' }}>Cohort: {session.cohortName}</div>
                <div style={{ fontSize: 10, fontFamily: 'var(--mono)', color: 'var(--gray-200)', marginTop: 6, wordBreak: 'break-all' }}>{session.token?.substring(0, 20)}...</div>
              </>
            ) : (
              <div style={{ padding: '40px 0', color: 'var(--gray-400)' }}>
                <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 12 }}>
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ color: 'var(--gray-300)' }}>
                    <rect x="3" y="3" width="18" height="18" rx="2" />
                    <rect x="7" y="7" width="3" height="3" />
                    <rect x="14" y="7" width="3" height="3" />
                    <rect x="7" y="14" width="3" height="3" />
                    <rect x="14" y="14" width="3" height="3" />
                  </svg>
                </div>
                <p style={{ fontSize: 13 }}>Generate a session to see the QR code</p>
                <div style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: 8 }}>{timerLabel}</div>
                <div style={{ fontFamily: 'var(--mono)', fontSize: 20, fontWeight: 500, color: displayRemaining < 300 ? 'var(--red)' : 'var(--gray-500)', marginTop: 4 }}>
                  {String(mins).padStart(2, '0')}:{String(secs).padStart(2, '0')}
                </div>
              </div>
            )}
          </Card>
        </div>
      </div>
    </>
  )
}

// ── Manual Attendance ────────────────────────────────────
export function FacilitatorManual() {
  const [cohorts, setCohorts]   = useState([])
  const [selected, setSelected] = useState('')
  const [summary, setSummary]   = useState(null)
  const [modal, setModal]       = useState(null) // { studentId, studentName }
  const [form, setForm]         = useState({ status: 'PRESENT', reason: '' })
  const [saving, setSaving]     = useState(false)

  useEffect(() => {
    facilitatorApi.myCohorts().then(r => {
      setCohorts(r.data)
      if (r.data.length > 0) { setSelected(r.data[0].id); loadSummary(r.data[0].id) }
    })
  }, [])

  const loadSummary = id => {
    facilitatorApi.todaySummary(id).then(r => setSummary(r.data)).catch(() => {})
  }

  const handleCohortChange = id => { setSelected(id); loadSummary(id) }

  const save = async () => {
    if (!form.reason.trim()) { toast.error('Reason is required'); return }
    setSaving(true)
    try {
      await facilitatorApi.manualAttend({ studentId: modal.studentId, status: form.status, reason: form.reason })
      toast.success(`Attendance updated — ${form.status}`)
      setModal(null)
      loadSummary(selected)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save')
    } finally {
      setSaving(false)
    }
  }

  const statusMap = summary?.records?.reduce((m, r) => { m[r.studentId] = r; return m }, {}) || {}

  return (
    <>
      <PageHeader title="Manual Attendance" subtitle="Override attendance for exceptional cases" />
      <div style={{ padding: 24 }} className="fade-in">
        <Alert type="warning">Manual attendance requires a reason and is fully logged in the audit trail.</Alert>
        <Card style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
            <Select label="" value={selected} onChange={e => handleCohortChange(e.target.value)} style={{ marginBottom: 0, minWidth: 180 }}>
              {cohorts.map(c => <option key={c.id} value={c.id}>{c.name} ({c.studentCount || 0} students)</option>)}
            </Select>
            <Button variant="outline" size="sm" onClick={() => loadSummary(selected)}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> Refresh</Button>
          </div>
        </Card>
        {summary && (
          <Card>
            <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
              {[['Present', summary.present, 'green'], ['Late', summary.late, 'yellow'], ['Absent', summary.absent, 'red']].map(([l, v, c]) => (
                <div key={l} style={{ padding: '8px 16px', borderRadius: 8, background: `var(--${c === 'green' ? 'green' : c === 'yellow' ? 'yellow' : 'red'}-light)`, border: `1px solid ${c === 'green' ? '#a8dbb8' : c === 'yellow' ? '#f3dfa8' : 'var(--red-mid)'}` }}>
                  <div style={{ fontSize: 20, fontWeight: 600 }}>{v}</div>
                  <div style={{ fontSize: 11, color: 'var(--gray-400)' }}>{l}</div>
                </div>
              ))}
            </div>
            <Table
              columns={[
                { key: 'studentName', label: 'Student', strong: true },
                { key: 'status',      label: 'Status',  render: (_, row) => <Badge status={statusMap[row.studentId]?.status || 'ABSENT'} /> },
                { key: 'markedAt',    label: 'Time',    render: (_, row) => statusMap[row.studentId]?.markedAt ? format(new Date(statusMap[row.studentId].markedAt), 'HH:mm') : '—' },
                { key: 'action',      label: '',        render: (_, row) => <Button size="sm" variant="outline" onClick={() => { setModal({ studentId: row.studentId, studentName: row.studentName }); setForm({ status: 'PRESENT', reason: '' }) }}>Edit</Button> },
              ]}
              rows={(summary.records || []).map(r => ({ ...r, studentId: r.studentId, studentName: r.studentName }))}
            />
          </Card>
        )}
      </div>

      <Modal open={!!modal} onClose={() => setModal(null)} title={`Manual Attendance — ${modal?.studentName}`}>
        <p style={{ fontSize: 13, color: 'var(--gray-400)', marginBottom: 16 }}>This will be saved and logged in the audit trail.</p>
        <Select label="Status" value={form.status} onChange={e => setForm(p => ({ ...p, status: e.target.value }))}>
          <option value="PRESENT">Present</option>
          <option value="LATE">Late</option>
          <option value="EXCUSED">Excused</option>
          <option value="ABSENT">Absent</option>
        </Select>
        <Textarea label="Reason (required)" value={form.reason} onChange={e => setForm(p => ({ ...p, reason: e.target.value }))} placeholder="e.g. Device malfunction, medical appointment..." />
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button variant="outline" onClick={() => setModal(null)}>Cancel</Button>
          <Button loading={saving} onClick={save}>Save Override</Button>
        </div>
      </Modal>
    </>
  )
}

// ── Reports ──────────────────────────────────────────────
export function FacilitatorReports() {
  const [cohorts, setCohorts] = useState([])
  const [selected, setSelected] = useState('')
  const [summary, setSummary]   = useState(null)
  const [loading, setLoading]   = useState(false)

  useEffect(() => {
    facilitatorApi.myCohorts().then(r => {
      setCohorts(r.data)
      if (r.data.length > 0) { setSelected(r.data[0].id); load(r.data[0].id) }
    })
  }, [])

  const load = id => {
    setLoading(true)
    facilitatorApi.todaySummary(id).then(r => setSummary(r.data)).finally(() => setLoading(false))
  }

  const exportCSV = () => {
    if (!summary?.records?.length) return
    const rows = [['Student', 'Cohort', 'Date', 'Status', 'Type', 'Reason']]
    summary.records.forEach(r => rows.push([r.studentName, r.cohortName, r.date, r.status, r.manual ? 'Manual' : 'QR', r.manualReason || '']))
    const csv = rows.map(r => r.map(c => `"${c ?? ''}"`).join(',')).join('\n')
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); a.download = `attendance_${selected}_${format(new Date(), 'yyyy-MM-dd')}.csv`; a.click()
    toast.success('CSV exported')
  }

  return (
    <>
      <PageHeader title="Reports" subtitle="Attendance data for your cohorts"
        actions={<Button variant="outline" size="sm" onClick={exportCSV}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Export CSV</Button>} />
      <div style={{ padding: 24 }} className="fade-in">
        <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
          <Select value={selected} onChange={e => { setSelected(e.target.value); load(e.target.value) }} style={{ marginBottom: 0, minWidth: 200 }}>
            {cohorts.map(c => <option key={c.id} value={c.id}>{c.name} ({c.studentCount || 0} students)</option>)}
          </Select>
            <Button variant="outline" size="sm" onClick={() => load(selected)}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> Refresh</Button>
        </div>
        {summary && (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(120px,1fr))', gap: 12, marginBottom: 20 }}>
              <StatCard label="Total"   value={summary.total}   />
              <StatCard label="Present" value={summary.present} badgeColor="green" progress={summary.total ? summary.present / summary.total * 100 : 0} />
              <StatCard label="Late"    value={summary.late}    badgeColor="yellow" />
              <StatCard label="Absent"  value={summary.absent}  badgeColor="red" />
              <StatCard label="Rate"    value={`${Math.round(summary.rate)}%`} progress={summary.rate} />
            </div>
            <Card>
              {loading ? <div style={{ textAlign: 'center', padding: 32 }}><div className="spinner spinner-lg" style={{ margin: '0 auto' }} /></div> : (
                <Table
                  columns={[
                    { key: 'studentName', label: 'Student', strong: true },
                    { key: 'status',      label: 'Status',  render: v => <Badge status={v} /> },
                    { key: 'markedAt',    label: 'Time',    render: v => v ? <span style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>{format(new Date(v), 'HH:mm')}</span> : '—' },
                    { key: 'manual',      label: 'Type',    render: v => <Badge status={v ? 'MANUAL' : 'ACTIVE'} /> },
                    { key: 'manualReason',label: 'Note',    render: v => v ? <span style={{ fontSize: 11, color: 'var(--gray-400)' }}>{v}</span> : null },
                  ]}
                  rows={summary.records || []}
                />
              )}
            </Card>
          </>
        )}
      </div>
    </>
  )
}

// ── Excuse Requests Review ───────────────────────────────
export function FacilitatorExcuses() {
  const [cohorts, setCohorts]     = useState([])
  const [selected, setSelected]   = useState('')
  const [requests, setRequests]   = useState([])
  const [loading, setLoading]     = useState(true)
  const [reviewModal, setReview]  = useState(null) // { id, studentName }
  const [form, setForm]           = useState({ status: 'ACCEPTED', notes: '' })
  const [saving, setSaving]       = useState(false)

  useEffect(() => {
    facilitatorApi.myCohorts().then(r => {
      setCohorts(r.data)
      if (r.data.length > 0) { setSelected(r.data[0].id); load(r.data[0].id) }
    }).finally(() => setLoading(false))
  }, [])

  const load = id => {
    if (!id) return
    facilitatorApi.cohortExcuses(id)
      .then(r => setRequests(r.data))
      .catch(() => toast.error('Failed to load excuse requests'))
  }

  const handleCohortChange = id => {
    setSelected(id)
    load(id)
  }

  const saveReview = async () => {
    setSaving(true)
    try {
      await facilitatorApi.reviewExcuse(reviewModal.id, form)
      toast.success(`Excuse request ${form.status.toLowerCase()}`)
      setReview(null)
      load(selected)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit review')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingPage />

  return (
    <>
      <PageHeader title="Excuse Requests" subtitle="Review and accept or reject student absence excuses" />
      <div style={{ padding: 24 }} className="fade-in">
        <Card style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <Select label="" value={selected} onChange={e => handleCohortChange(e.target.value)} style={{ marginBottom: 0, minWidth: 200 }}>
              {cohorts.map(c => <option key={c.id} value={c.id}>{c.name} ({c.studentCount || 0} students)</option>)}
            </Select>
          <Button variant="outline" size="sm" onClick={() => load(selected)}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> Refresh</Button>
          </div>
        </Card>
        <Card>
          <Table
            columns={[
              { key: 'studentName',  label: 'Student',    strong: true },
              { key: 'startDate',    label: 'Start Date', render: v => v ? format(new Date(v), 'dd MMM yyyy') : '—' },
              { key: 'numberOfDays', label: 'Days',       render: v => `${v} day${v > 1 ? 's' : ''}` },
              { key: 'reason',       label: 'Reason' },
              { key: 'coverUpPlan',  label: 'Cover-Up Plan', render: v => <span style={{ fontSize: 12, color: 'var(--gray-600)' }}>{v}</span> },
              { key: 'status',       label: 'Status',     render: v => <Badge status={v} /> },
              { key: 'actions',      label: 'Actions',    render: (_, row) => (
                row.status === 'PENDING' ? (
                  <Button size="sm" variant="outline" onClick={() => { setReview(row); setForm({ status: 'ACCEPTED', notes: '' }) }}>Review Request</Button>
                ) : (
                  <span style={{ fontSize: 11, color: 'var(--gray-400)' }}>Reviewed by {row.reviewedByName}</span>
                )
              )}
            ]}
            rows={requests}
            emptyMessage="No excuse requests found for this cohort"
          />
        </Card>
      </div>

      <Modal open={!!reviewModal} onClose={() => setReview(null)} title={`Review Excuse Request — ${reviewModal?.studentName}`}>
        <div style={{ background: 'var(--off)', padding: 12, borderRadius: 8, marginBottom: 16 }}>
          <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Reason:</div>
          <div style={{ fontSize: 13, color: 'var(--gray-700)', marginBottom: 8 }}>{reviewModal?.reason}</div>
          <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Cover-up Plan:</div>
          <div style={{ fontSize: 12, color: 'var(--gray-600)' }}>{reviewModal?.coverUpPlan}</div>
        </div>
        <Select label="Decision" value={form.status} onChange={e => setForm(p => ({ ...p, status: e.target.value }))}>
          <option value="ACCEPTED">Accept Excuse</option>
          <option value="REJECTED">Reject Excuse</option>
        </Select>
        <Textarea label="Reviewer Notes / Feedback" value={form.notes} onChange={e => setForm(p => ({ ...p, notes: e.target.value }))} placeholder="Provide reasoning or instructions for student..." rows={3} />
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16 }}>
          <Button variant="outline" onClick={() => setReview(null)}>Cancel</Button>
          <Button loading={saving} onClick={saveReview}>Submit Decision</Button>
        </div>
      </Modal>
    </>
  )
}
