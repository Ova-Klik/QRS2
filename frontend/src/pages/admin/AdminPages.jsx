import React, { useState, useEffect, useCallback } from 'react'
import { adminApi } from '../../api/client'
import { Card, StatCard, Badge, Table, PageHeader, LoadingPage, Alert, Button, Modal, Input, Select, Textarea } from '../../components/common/UI'
import toast from 'react-hot-toast'
import { format } from 'date-fns'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'

// ── Dashboard ────────────────────────────────────────────
export function AdminDashboard() {
  const [data, setData]     = useState(null)
  const [loading, setLoading] = useState(true)
  useEffect(() => { adminApi.schoolStats().then(r => setData(r.data)).catch(() => toast.error('Failed to load')).finally(() => setLoading(false)) }, [])
  if (loading) return <LoadingPage />
  const d = data || {}
  const pieData = [
    { name: 'Present', value: d.presentToday || 0, color: '#22c55e' },
    { name: 'Late',    value: d.lateToday    || 0, color: '#f59e0b' },
    { name: 'Excused', value: d.excusedToday || 0, color: '#1d4ed8' },
    { name: 'Absent',  value: d.absentToday  || 0, color: '#C0392B' },
  ]
  return (
    <>
      <PageHeader title="Admin Dashboard" subtitle={`School-wide overview — ${format(new Date(), 'EEEE, dd MMM yyyy')}`} />
      <div style={{ padding: 24 }} className="fade-in">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12, marginBottom: 24 }}>
          <StatCard label="Total Students"    value={d.totalStudents    || 0} />
          <StatCard label="Facilitators"      value={d.totalFacilitators || 0} />
          <StatCard label="Active Cohorts"    value={d.activeCohorts    || 0} />
          <StatCard label="Present Today"     value={(d.presentToday || 0) + (d.lateToday || 0)} badge="incl. late" badgeColor="green" />
          <StatCard label="Excused Today"     value={d.excusedToday     || 0} badge="approved" badgeColor="gray" />
          <StatCard label="Attendance Rate"   value={`${Math.round(d.schoolAttendanceRate || 0)}%`} progress={d.schoolAttendanceRate} />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 16, marginBottom: 20 }}>
          <Card>
            <div style={{ fontWeight: 600, marginBottom: 16 }}>Cohort Performance</div>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={(d.cohorts || []).map(c => ({ name: c.name, rate: Math.round(c.attendanceRate) }))}>
                <XAxis dataKey="name" style={{ fontSize: 11 }} />
                <YAxis domain={[0,100]} style={{ fontSize: 11 }} />
                <Tooltip formatter={v => `${v}%`} />
                <Bar dataKey="rate" fill="var(--red)" radius={[4,4,0,0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
          <Card style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ fontWeight: 600, marginBottom: 12, alignSelf: 'flex-start' }}>Today's Breakdown</div>
            <PieChart width={160} height={160}>
              <Pie data={pieData} cx={75} cy={75} innerRadius={45} outerRadius={70} dataKey="value">
                {pieData.map((e, i) => <Cell key={i} fill={e.color} />)}
              </Pie>
            </PieChart>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', justifyContent: 'center', marginTop: 8 }}>
              {pieData.map(p => (
                <div key={p.name} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11 }}>
                  <div style={{ width: 8, height: 8, borderRadius: '50%', background: p.color }} />
                  {p.name}: {p.value}
                </div>
              ))}
            </div>
          </Card>
        </div>
        <Card>
          <div style={{ fontWeight: 600, marginBottom: 16 }}>Recent Activity</div>
          {(d.recentActivity || []).map((a, i) => (
            <div key={i} style={{ padding: '8px 0', borderBottom: i < (d.recentActivity.length - 1) ? '1px solid var(--gray-50)' : 'none' }}>
              <div style={{ fontSize: 13, fontWeight: 500 }}>{a.action?.replace(/_/g, ' ')}</div>
              <div style={{ fontSize: 12, color: 'var(--gray-400)' }}>{a.actor} — {a.detail}</div>
              <div style={{ fontSize: 10, fontFamily: 'var(--mono)', color: 'var(--gray-200)', marginTop: 2 }}>{a.ts}</div>
            </div>
          ))}
        </Card>
      </div>
    </>
  )
}

// ── Students ─────────────────────────────────────────────
export function AdminStudents() {
  const [students, setStudents] = useState([])
  const [loading, setLoading]   = useState(true)
  const [modal, setModal]       = useState(null) // 'add' | {user}
  const [resetModal, setReset]  = useState(null) // userId
  const [form, setForm]         = useState({ name: '', email: '', password: '', cohortId: '' })
  const [cohorts, setCohorts]   = useState([])
  const [saving, setSaving]     = useState(false)
  const [resetPass, setResetPass] = useState('')

  const load = useCallback(() => {
    Promise.all([adminApi.listUsers('student'), adminApi.listCohorts()]).then(([u, c]) => { setStudents(u.data); setCohorts(c.data) }).finally(() => setLoading(false))
  }, [])
  useEffect(() => { load() }, [load])

  const addStudent = async () => {
    if (!form.name || !form.email || !form.password) { toast.error('Fill all required fields'); return }
    setSaving(true)
    try {
      await adminApi.createUser({ ...form, role: 'STUDENT' })
      toast.success('Student added'); setModal(null); load()
    } catch (err) { toast.error(err.response?.data?.message || 'Failed to add student') }
    finally { setSaving(false) }
  }

  const toggleActive = async (student) => {
    await adminApi.updateUser(student.id, { active: !student.active })
    toast.success(student.active ? 'Student deactivated' : 'Student activated'); load()
  }

  const unlockDevice = async (studentId) => {
    await adminApi.unlockDevice(studentId)
    toast.success('Device unlocked'); load()
  }

  const doResetPass = async () => {
    if (!resetPass || resetPass.length < 6) { toast.error('Password too short'); return }
    setSaving(true)
    try {
      await adminApi.resetPassword({ userId: resetModal, newPassword: resetPass })
      toast.success('Password reset'); setReset(null); setResetPass('')
    } catch { toast.error('Failed') }
    finally { setSaving(false) }
  }

  if (loading) return <LoadingPage />

  return (
    <>
      <PageHeader title="Students" subtitle={`${students.length} registered students`}
        actions={<Button onClick={() => { setModal('add'); setForm({ name: '', email: '', password: 'Student@1234', cohortId: cohorts[0]?.id || '' }) }}>+ Add Student</Button>} />
      <div style={{ padding: 24 }} className="fade-in">
        <Card>
          <Table
            columns={[
              { key: 'name',         label: 'Name',      strong: true },
              { key: 'email',        label: 'Email',     render: v => <span style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--gray-400)' }}>{v}</span> },
              { key: 'cohortId',     label: 'Cohort',    render: (_, row) => cohorts.find(c => c.id === row.cohortId)?.name || '—' },
              { key: 'device',       label: 'Device Lock', render: (_, row) => row.device?.locked && row.device?.fingerprint ? <Badge status="PRESENT" label="Locked" /> : <Badge status="EXCUSED" label="Cleared" /> },
              { key: 'attendance',   label: 'Rate',      render: (_, row) => row.attendanceSummary ? `${Math.round(row.attendanceSummary.rate)}%` : '—' },
              { key: 'active',       label: 'Status',    render: v => <Badge status={v ? 'ACTIVE' : 'INACTIVE'} /> },
              { key: 'actions',      label: 'Actions',    render: (_, row) => (
                <div style={{ display: 'flex', gap: 6 }}>
                  <Button size="sm" variant="outline" onClick={() => unlockDevice(row.id)}>Reset Device</Button>
                  <Button size="sm" variant="outline" onClick={() => setReset(row.id)}>Reset Pwd</Button>
                  <Button size="sm" variant="outline" onClick={() => toggleActive(row)}>{row.active ? 'Deactivate' : 'Activate'}</Button>
                </div>
              )},
            ]}
            rows={students}
          />
        </Card>
      </div>

      {/* Add Student Modal */}
      <Modal open={modal === 'add'} onClose={() => setModal(null)} title="Add Student">
        <Input label="Full Name *"  value={form.name}     onChange={e => setForm(p => ({...p, name: e.target.value}))} placeholder="Ada Okafor" />
        <Input label="Email *"      value={form.email}    onChange={e => setForm(p => ({...p, email: e.target.value}))} type="email" placeholder="ada@techschool.edu" />
        <Input label="Password *"   value={form.password} onChange={e => setForm(p => ({...p, password: e.target.value}))} type="password" />
        <Select label="Cohort"      value={form.cohortId} onChange={e => setForm(p => ({...p, cohortId: e.target.value}))}>
          {cohorts.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button variant="outline" onClick={() => setModal(null)}>Cancel</Button>
          <Button loading={saving} onClick={addStudent}>Add Student</Button>
        </div>
      </Modal>

      {/* Reset Password Modal */}
      <Modal open={!!resetModal} onClose={() => setReset(null)} title="Reset Password">
        <Input label="New Password" type="password" value={resetPass} onChange={e => setResetPass(e.target.value)} placeholder="Min. 6 characters" />
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button variant="outline" onClick={() => setReset(null)}>Cancel</Button>
          <Button loading={saving} onClick={doResetPass}>Reset</Button>
        </div>
      </Modal>
    </>
  )
}

// ── Facilitators ─────────────────────────────────────────
export function AdminFacilitators() {
  const [facilitators, setFacilitators] = useState([])
  const [cohorts, setCohorts]           = useState([])
  const [loading, setLoading]           = useState(true)
  const [modal, setModal]               = useState(false)
  const [form, setForm]                 = useState({ name: '', email: '', password: 'Fac@1234', assignedCohortIds: [] })
  const [saving, setSaving]             = useState(false)

  const load = useCallback(() => {
    Promise.all([adminApi.listUsers('facilitator'), adminApi.listCohorts()]).then(([u, c]) => { setFacilitators(u.data); setCohorts(c.data) }).finally(() => setLoading(false))
  }, [])
  useEffect(() => { load() }, [load])

  const addFac = async () => {
    if (!form.name || !form.email) { toast.error('Fill all required fields'); return }
    setSaving(true)
    try {
      await adminApi.createUser({ ...form, role: 'FACILITATOR' })
      toast.success('Facilitator added'); setModal(false); load()
    } catch (err) { toast.error(err.response?.data?.message || 'Failed') }
    finally { setSaving(false) }
  }

  if (loading) return <LoadingPage />

  return (
    <>
      <PageHeader title="Facilitators" subtitle={`${facilitators.length} facilitators`}
        actions={<Button size="sm" onClick={() => setModal(true)}>+ Add Facilitator</Button>} />
      <div style={{ padding: 24 }} className="fade-in">
        <Card>
          <Table
            columns={[
              { key: 'name',              label: 'Name',    strong: true },
              { key: 'email',             label: 'Email',   render: v => <span style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--gray-400)' }}>{v}</span> },
              { key: 'assignedCohortIds', label: 'Cohorts', render: v => (v || []).map(id => cohorts.find(c => c.id === id)?.name).filter(Boolean).join(', ') || '—' },
              { key: 'active',            label: 'Status',  render: v => <Badge status={v ? 'ACTIVE' : 'INACTIVE'} /> },
            ]}
            rows={facilitators}
          />
        </Card>
      </div>
      <Modal open={modal} onClose={() => setModal(false)} title="Add Facilitator">
        <Input label="Full Name *"  value={form.name}     onChange={e => setForm(p => ({...p, name: e.target.value}))} />
        <Input label="Email *"      value={form.email}    onChange={e => setForm(p => ({...p, email: e.target.value}))} type="email" />
        <Input label="Password *"   value={form.password} onChange={e => setForm(p => ({...p, password: e.target.value}))} type="password" />
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button variant="outline" onClick={() => setModal(false)}>Cancel</Button>
          <Button loading={saving} onClick={addFac}>Add Facilitator</Button>
        </div>
      </Modal>
    </>
  )
}

// ── Cohorts ──────────────────────────────────────────────
export function AdminCohorts() {
  const [cohorts, setCohorts]       = useState([])
  const [facilitators, setFacs]     = useState([])
  const [loading, setLoading]       = useState(true)
  const [modal, setModal]           = useState(false)
  const [form, setForm]             = useState({ name: '', facilitatorId: '' })
  const [saving, setSaving]         = useState(false)

  const load = useCallback(() => {
    Promise.all([adminApi.listCohorts(), adminApi.listUsers('facilitator')]).then(([c, f]) => { setCohorts(c.data); setFacs(f.data) }).finally(() => setLoading(false))
  }, [])
  useEffect(() => { load() }, [load])

  const add = async () => {
    if (!form.name || !form.facilitatorId) { toast.error('Fill all fields'); return }
    setSaving(true)
    try { await adminApi.createCohort(form); toast.success('Cohort created'); setModal(false); load() }
    catch (err) { toast.error(err.response?.data?.message || 'Failed') }
    finally { setSaving(false) }
  }

  const toggle = async id => { await adminApi.toggleCohort(id); load() }

  if (loading) return <LoadingPage />

  return (
    <>
      <PageHeader title="Cohorts" subtitle={`${cohorts.filter(c => c.active).length} active cohorts`}
        actions={<Button size="sm" onClick={() => { setModal(true); setForm({ name: '', facilitatorId: facilitators[0]?.id || '' }) }}>+ Add Cohort</Button>} />
      <div style={{ padding: 24 }} className="fade-in">
        <Card>
          <Table
            columns={[
              { key: 'name',             label: 'Name',        strong: true },
              { key: 'facilitatorName',  label: 'Facilitator' },
              { key: 'studentCount',     label: 'Students' },
              { key: 'schedule',         label: 'Schedule',    render: v => <span style={{ fontSize: 12, fontFamily: 'var(--mono)', color: 'var(--gray-400)' }}>{v}</span> },
              { key: 'attendanceRate',   label: 'Att. Rate',   render: v => `${Math.round(v)}%` },
              { key: 'active',           label: 'Status',      render: v => <Badge status={v ? 'ACTIVE' : 'INACTIVE'} /> },
              { key: 'actions',          label: '',            render: (_, row) => <Button size="sm" variant="outline" onClick={() => toggle(row.id)}>{row.active ? 'Deactivate' : 'Activate'}</Button> },
            ]}
            rows={cohorts}
          />
        </Card>
      </div>
      <Modal open={modal} onClose={() => setModal(false)} title="Add Cohort">
        <Input label="Cohort Name *" value={form.name} onChange={e => setForm(p => ({...p, name: e.target.value}))} placeholder="e.g. Cohort 32" />
        <Select label="Facilitator *" value={form.facilitatorId} onChange={e => setForm(p => ({...p, facilitatorId: e.target.value}))}>
          {facilitators.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
        </Select>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button variant="outline" onClick={() => setModal(false)}>Cancel</Button>
          <Button loading={saving} onClick={add}>Create Cohort</Button>
        </div>
      </Modal>
    </>
  )
}

// ── Devices ──────────────────────────────────────────────
export function AdminDevices() {
  const [students, setStudents] = useState([])
  const [loading, setLoading]   = useState(true)

  const load = useCallback(() => {
    adminApi.listUsers('student').then(r => setStudents(r.data)).finally(() => setLoading(false))
  }, [])
  useEffect(() => { load() }, [load])

  const unlock = async id => { await adminApi.unlockDevice(id); toast.success('Device unlocked'); load() }

  const register = async (studentId, fingerprint) => {
    await adminApi.registerDevice({ studentId, fingerprint, userAgent: navigator.userAgent })
    toast.success('Device registered'); load()
  }

  if (loading) return <LoadingPage />
  const withDevices = students.filter(s => s.device)
  const locked      = withDevices.filter(s => s.device?.locked).length
  const unlocked    = withDevices.filter(s => !s.device?.locked).length

  return (
    <>
      <PageHeader title="Devices" subtitle="Student device registry" />
      <div style={{ padding: 24 }} className="fade-in">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12, marginBottom: 20 }}>
          <StatCard label="Total Devices"  value={withDevices.length} />
          <StatCard label="Registered"     value={locked}   badgeColor="green" badge="Active" />
          <StatCard label="Unlocked"       value={unlocked} badgeColor="red"   badge="Needs action" color={unlocked > 0 ? 'var(--red)' : undefined} />
        </div>
        <Card>
          <Table
            columns={[
              { key: 'name',        label: 'Student',      strong: true },
              { key: 'device',      label: 'Fingerprint',  render: (v) => v?.fingerprint ? <span style={{ fontFamily: 'var(--mono)', fontSize: 11 }}>{v.fingerprint}</span> : '—' },
              { key: 'device2',     label: 'Status',       render: (_, row) => <Badge status={row.device?.locked ? 'ACTIVE' : 'ABSENT'} /> },
              { key: 'device3',     label: 'Registered',   render: (_, row) => row.device?.registeredAt ? format(new Date(row.device.registeredAt), 'dd MMM yyyy') : '—' },
              { key: 'action',      label: '',             render: (_, row) => row.device?.locked
                ? <Button size="sm" variant="outline" onClick={() => unlock(row.id)}>Unlock</Button>
                : <Button size="sm" onClick={() => register(row.id, 'FP-' + row.id.substring(0,8).toUpperCase())}>Register</Button>
              },
            ]}
            rows={students}
          />
        </Card>
      </div>
    </>
  )
}

// ── Audit Logs ───────────────────────────────────────────
export function AdminAudit() {
  const [logs, setLogs]     = useState([])
  const [loading, setLoading] = useState(true)
  useEffect(() => { adminApi.auditLogs().then(r => setLogs(r.data)).finally(() => setLoading(false)) }, [])
  if (loading) return <LoadingPage />
  return (
    <>
      <PageHeader title="Audit Logs" subtitle="Full system activity trail" />
      <div style={{ padding: 24 }} className="fade-in">
        <Card>
          <Table
            columns={[
              { key: 'action',      label: 'Action',    strong: true, render: v => v?.replace(/_/g,' ') },
              { key: 'actorName',   label: 'Actor' },
              { key: 'targetName',  label: 'Target' },
              { key: 'detail',      label: 'Detail',    render: v => <span style={{ fontSize: 12, color: 'var(--gray-400)' }}>{v}</span> },
              { key: 'createdAt',   label: 'Timestamp', render: v => v ? <span style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--gray-400)' }}>{format(new Date(v), 'dd MMM HH:mm:ss')}</span> : '—' },
            ]}
            rows={logs}
          />
        </Card>
      </div>
    </>
  )
}

// ── Analytics ────────────────────────────────────────────
export function AdminAnalytics() {
  const [data, setData]     = useState(null)
  const [students, setStudents] = useState([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    Promise.all([adminApi.schoolStats(), adminApi.listUsers('student')]).then(([s, u]) => { setData(s.data); setStudents(u.data) }).finally(() => setLoading(false))
  }, [])
  if (loading) return <LoadingPage />
  const d = data || {}
  const behaviourList = d.studentBehaviour || []

  const dayOfWeekChartData = Object.entries(d.dayOfWeekBreakdown || {}).map(([day, map]) => ({
    day: day.charAt(0) + day.slice(1, 3).toLowerCase(),
    Present: map.PRESENT || 0,
    Late: map.LATE || 0,
    Excused: map.EXCUSED || 0,
    Absent: map.ABSENT || 0,
  }))

  const exportCSV = () => {
    const rows = [['Student','Cohort','Total','Present','Late','Absent','Excused','Rate%','Behavior Tag','Insight']]
    behaviourList.forEach(b => {
      rows.push([b.studentName, b.cohortName, b.totalRecords, b.present, b.late, b.absent, b.excused, Math.round(b.attendanceRate), b.behaviorTag, b.behaviorInsightText])
    })
    const csv = rows.map(r => r.map(c => `"${c}"`).join(',')).join('\n')
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv],{type:'text/csv'})); a.download=`behaviour_analytics_${format(new Date(),'yyyy-MM-dd')}.csv`; a.click()
    toast.success('Behavior CSV exported')
  }

  const renderTagBadge = (tag) => {
    switch (tag) {
      case 'EXCELLENT':      return <Badge status="PRESENT" label="Model Student" />
      case 'GOOD_STANDING':  return <Badge status="ACTIVE" label="Good Standing" />
      case 'CHRONIC_LATE':   return <Badge status="LATE" label="Frequent Late" />
      case 'HIGH_EXCUSES':   return <Badge status="EXCUSED" label="High Excuses" />
      case 'CHRONIC_ABSENT': return <Badge status="ABSENT" label="At-Risk (<75%)" />
      default:               return <Badge status="INACTIVE" label={tag} />
    }
  }

  return (
    <>
      <PageHeader title="Analytics & Behaviour Insights" subtitle="School-wide attendance metrics, day-of-week trends, and student behavior risk patterns"
        actions={<Button variant="outline" size="sm" onClick={exportCSV}>↓ Export Behaviour CSV</Button>} />
      <div style={{ padding: 24 }} className="fade-in">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 12, marginBottom: 24 }}>
          <StatCard label="School Rate"      value={`${Math.round(d.schoolAttendanceRate||0)}%`} progress={d.schoolAttendanceRate} />
          <StatCard label="Present Today"   value={d.presentToday||0} badgeColor="green" />
          <StatCard label="Late Today"      value={d.lateToday||0}    badgeColor="yellow" />
          <StatCard label="Excused Today"   value={d.excusedToday||0} badge="approved" badgeColor="gray" />
          <StatCard label="Absent Today"    value={d.absentToday||0}  badgeColor="red" />
          <StatCard label="Total Excused"   value={d.totalExcusedAllTime||0} badge="all-time" badgeColor="gray" />
        </div>

        {/* Day of Week Behaviour Patterns */}
        <Card style={{ marginBottom: 24 }}>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>Day-of-Week Attendance Patterns</div>
          <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 16 }}>Breakdown of attendance statuses across days of the week</p>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={dayOfWeekChartData}>
              <XAxis dataKey="day" style={{ fontSize: 11 }} />
              <YAxis style={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="Present" fill="#22c55e" radius={[3,3,0,0]} />
              <Bar dataKey="Late" fill="#f59e0b" radius={[3,3,0,0]} />
              <Bar dataKey="Excused" fill="#1d4ed8" radius={[3,3,0,0]} />
              <Bar dataKey="Absent" fill="#C0392B" radius={[3,3,0,0]} />
            </BarChart>
          </ResponsiveContainer>
        </Card>

        {/* Student Behaviour & Risk Analytics Matrix */}
        <Card>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>Student Behaviour & Punctuality Risk Analytics</div>
          <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 16 }}>Automated behavioral pattern classification derived from attendance & excuse history</p>
          <Table
            columns={[
              { key: 'studentName',        label: 'Student',  strong: true },
              { key: 'cohortName',         label: 'Cohort' },
              { key: 'present',            label: 'Present', render: v => <span style={{ color: 'var(--green-dark)', fontWeight: 500 }}>{v}</span> },
              { key: 'late',               label: 'Late',    render: v => <span style={{ color: 'var(--yellow-dark)', fontWeight: 500 }}>{v}</span> },
              { key: 'excused',            label: 'Excused', render: v => <span style={{ color: '#1d4ed8', fontWeight: 500 }}>{v}</span> },
              { key: 'absent',             label: 'Absent',  render: v => <span style={{ color: 'var(--red)', fontWeight: 500 }}>{v}</span> },
              { key: 'attendanceRate',     label: 'Rate',    render: (v) => {
                const r = Math.round(v || 0)
                return (
                  <div style={{ display:'flex', alignItems:'center', gap:8 }}>
                    <span style={{ fontWeight:500, fontSize:12 }}>{r}%</span>
                    <div style={{ width:50, height:4, background:'var(--gray-100)', borderRadius:2 }}>
                      <div style={{ width:`${r}%`, height:'100%', background: r>=80?'var(--green)':r>=60?'#f59e0b':'var(--red)', borderRadius:2 }} />
                    </div>
                  </div>
                )
              }},
              { key: 'behaviorTag',        label: 'Behavior Pattern', render: (v) => renderTagBadge(v) },
              { key: 'behaviorInsightText',label: 'Behavioral Observation', render: v => <span style={{ fontSize: 12, color: 'var(--gray-600)' }}>{v}</span> },
            ]}
            rows={behaviourList}
            emptyMessage="No student behavioral data available yet"
          />
        </Card>
      </div>
    </>
  )
}

// ── Settings ─────────────────────────────────────────────
export function AdminSettings() {
  const { useAuth } = require('../../context/AuthContext')
  const { user } = useAuth ? useAuth() : {}
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [loading, setLoading] = useState(false)
  const [netSettings, setNetSettings] = useState(null)
  const [netLoading, setNetLoading] = useState(true)
  const [netSaving, setNetSaving] = useState(false)

  useEffect(() => {
    adminApi.getNetworkSettings().then(r => setNetSettings(r.data)).catch(() => toast.error('Failed to load network settings')).finally(() => setNetLoading(false))
  }, [])

  const save = async e => {
    e.preventDefault()
    if (form.newPassword !== form.confirm) { toast.error('Passwords do not match'); return }
    setLoading(true)
    try {
      const api = (await import('../../api/client')).default
      await api.post('/auth/change-password', { currentPassword: form.currentPassword, newPassword: form.newPassword })
      toast.success('Password changed')
      setForm({ currentPassword: '', newPassword: '', confirm: '' })
    } catch (err) { toast.error(err.response?.data?.message || 'Failed') }
    finally { setLoading(false) }
  }

  const saveNetwork = async () => {
    setNetSaving(true)
    try {
      await adminApi.updateNetworkSettings(netSettings)
      toast.success('Network settings saved')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save network settings')
    } finally {
      setNetSaving(false)
    }
  }

  const updateNet = (key, value) => setNetSettings(prev => ({ ...prev, [key]: value }))

  return (
    <>
      <PageHeader title="Settings" subtitle="System configuration" />
      <div style={{ padding: 24, maxWidth: 600 }} className="fade-in">
        <Card style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 600, marginBottom: 12 }}>Account</div>
          <div style={{ fontSize: 13, color: 'var(--gray-600)' }}>Logged in as <strong>{user?.name}</strong></div>
          <div style={{ fontSize: 12, color: 'var(--gray-400)', fontFamily: 'var(--mono)' }}>{user?.email}</div>
        </Card>

        <Card style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 600, marginBottom: 16 }}>Network & Attendance Settings</div>
          <p style={{ fontSize: 12, color: 'var(--gray-400)', marginBottom: 16 }}>
            Configure the school network and attendance time windows. Changes take effect immediately.
          </p>
          {netLoading ? (
            <div style={{ textAlign: 'center', padding: 20 }}><div className="spinner" style={{ margin: '0 auto' }} /></div>
          ) : netSettings && (
            <>
              <div style={{ fontWeight: 500, fontSize: 13, marginBottom: 8, color: 'var(--gray-600)' }}>Network</div>
              <Input
                label="School WiFi SSID"
                value={netSettings.school_wifi_ssid || ''}
                onChange={e => updateNet('school_wifi_ssid', e.target.value)}
                placeholder="e.g. TechSchool-WiFi"
              />
              <Input
                label="School IP Range (CIDR)"
                value={netSettings.school_ip_range || ''}
                onChange={e => updateNet('school_ip_range', e.target.value)}
                placeholder="e.g. 192.168.1.0/24"
              />
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, color: 'var(--gray-600)' }}>
                  <input
                    type="checkbox"
                    checked={netSettings.network_enforce === 'true'}
                    onChange={e => updateNet('network_enforce', e.target.checked ? 'true' : 'false')}
                    style={{ width: 16, height: 16, accentColor: 'var(--red)' }}
                  />
                  Enforce school network for attendance
                </label>
                <p style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: 4, marginLeft: 24 }}>
                  When enabled, students must be connected to the school network to mark attendance
                </p>
              </div>
              <div style={{ height: 1, background: 'var(--gray-100)', margin: '16px 0' }} />
              <div style={{ fontWeight: 500, fontSize: 13, marginBottom: 8, color: 'var(--gray-600)' }}>GPS Geofence Fallback</div>
              <div style={{ marginBottom: 10 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, color: 'var(--gray-600)' }}>
                  <input
                    type="checkbox"
                    checked={netSettings.geofence_fallback_enabled === 'true'}
                    onChange={e => updateNet('geofence_fallback_enabled', e.target.checked ? 'true' : 'false')}
                    style={{ width: 16, height: 16, accentColor: 'var(--red)' }}
                  />
                  Enable GPS Geofence Fallback when school network is offline/disconnected
                </label>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
                <Input
                  label="School Latitude"
                  value={netSettings.school_latitude || ''}
                  onChange={e => updateNet('school_latitude', e.target.value)}
                  placeholder="e.g. 6.5244"
                />
                <Input
                  label="School Longitude"
                  value={netSettings.school_longitude || ''}
                  onChange={e => updateNet('school_longitude', e.target.value)}
                  placeholder="e.g. 3.3792"
                />
                <Input
                  label="Geofence Radius (meters)"
                  value={netSettings.school_geofence_radius_meters || ''}
                  onChange={e => updateNet('school_geofence_radius_meters', e.target.value)}
                  placeholder="e.g. 150"
                />
              </div>
              <div style={{ height: 1, background: 'var(--gray-100)', margin: '16px 0' }} />
              <div style={{ fontWeight: 500, fontSize: 13, marginBottom: 8, color: 'var(--gray-600)' }}>Attendance Time Windows</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
                <Input
                  label="QR Window Start"
                  value={netSettings.qr_window_start || ''}
                  onChange={e => updateNet('qr_window_start', e.target.value)}
                  placeholder="HH:MM"
                />
                <Input
                  label="QR Window End"
                  value={netSettings.qr_window_end || ''}
                  onChange={e => updateNet('qr_window_end', e.target.value)}
                  placeholder="HH:MM"
                />
                <Input
                  label="Late Threshold"
                  value={netSettings.late_threshold || ''}
                  onChange={e => updateNet('late_threshold', e.target.value)}
                  placeholder="HH:MM"
                />
              </div>
              <Button onClick={saveNetwork} loading={netSaving} style={{ marginTop: 4 }}>
                Save Network & Geofence Settings
              </Button>
            </>
          )}
        </Card>

        <Card>
          <div style={{ fontWeight: 600, marginBottom: 16 }}>Change Password</div>
          <form onSubmit={save}>
            <Input label="Current password" type="password" value={form.currentPassword} onChange={e => setForm(p => ({...p, currentPassword: e.target.value}))} />
            <Input label="New password" type="password" value={form.newPassword} onChange={e => setForm(p => ({...p, newPassword: e.target.value}))} />
            <Input label="Confirm new password" type="password" value={form.confirm} onChange={e => setForm(p => ({...p, confirm: e.target.value}))} />
            <Button type="submit" loading={loading}>Update Password</Button>
          </form>
        </Card>
      </div>
    </>
  )
}
