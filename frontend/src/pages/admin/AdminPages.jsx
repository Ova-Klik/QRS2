import React, { useState, useEffect, useCallback, useMemo } from 'react'
import { adminApi } from '../../api/client'
import { Card, StatCard, Badge, Table, PageHeader, LoadingPage, Alert, Button, Modal, Input, Select, Textarea, Pagination, Skeleton, useDebounce } from '../../components/common/UI'
import toast from 'react-hot-toast'
import { format } from 'date-fns'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'

// ── Dashboard ────────────────────────────────────────────
export function AdminDashboard() {
  const [cohorts, setCohorts]   = useState([])
  const [cohortId, setCohortId] = useState('')
  const [data, setData]         = useState(null)
  const [loading, setLoading]   = useState(true)

  useEffect(() => {
    adminApi.listCohorts().then(r => setCohorts(r.data || [])).catch(() => {})
  }, [])

  useEffect(() => {
    let alive = true
    setLoading(true)
    adminApi.schoolStats(cohortId || undefined)
      .then(r => { if (alive) setData(r.data) })
      .catch(() => alive && toast.error('Failed to load dashboard'))
      .finally(() => alive && setLoading(false))
    return () => { alive = false }
  }, [cohortId])

  const d = data || {}
  const pieData = [
    { name: 'Present', value: d.presentToday || 0, color: '#22c55e' },
    { name: 'Late',    value: d.lateToday    || 0, color: '#f59e0b' },
    { name: 'Excused', value: d.excusedToday || 0, color: '#1d4ed8' },
    { name: 'Absent',  value: d.absentToday  || 0, color: '#C0392B' },
  ]
  const chartData = (d.cohorts || []).map(c => ({
    name: c.name,
    rate: Math.round(c.attendanceRate),
    isSelected: cohortId ? c.id === cohortId : false,
  }))
  const weeklyData = Object.entries(d.dayOfWeekBreakdown || {}).map(([day, map]) => ({
    day: day.charAt(0) + day.slice(1, 3).toLowerCase(),
    Present: map.PRESENT || 0,
    Late: map.LATE || 0,
    Absent: map.ABSENT || 0,
  }))
  const behaviourList = (d.studentBehaviour || []).slice(0, 10)
  const tagBadge = (tag) => {
    switch (tag) {
      case 'EXCELLENT':      return <Badge status="PRESENT" label="Model" />
      case 'GOOD_STANDING':  return <Badge status="ACTIVE" label="Good Standing" />
      case 'CHRONIC_LATE':   return <Badge status="LATE" label="Frequent Late" />
      case 'HIGH_EXCUSES':   return <Badge status="EXCUSED" label="High Excuses" />
      case 'CHRONIC_ABSENT': return <Badge status="ABSENT" label="At-Risk" />
      default:               return <Badge status="INACTIVE" label={tag} />
    }
  }

  const exportCSV = () => {
    const rows = [['Cohort', 'Attendance Rate %', 'Present Today', 'Late Today', 'Excused Today', 'Absent Today', 'Students']]
    ;(d.cohorts || []).forEach(c => rows.push([c.name, Math.round(c.attendanceRate || 0), d.presentToday || 0, d.lateToday || 0, d.excusedToday || 0, d.absentToday || 0, c.studentCount]))
    const csv = rows.map(r => r.map(c => `"${c}"`).join(',')).join('\n')
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); a.download = `dashboard_${format(new Date(), 'yyyy-MM-dd')}.csv`; a.click()
    toast.success('Dashboard CSV exported')
  }

  if (loading && !data) return <LoadingPage />

  return (
    <>
      <PageHeader title="Admin Dashboard" subtitle={`School-wide overview — ${format(new Date(), 'EEEE, dd MMM yyyy')}`}
        actions={<Button variant="outline" size="sm" onClick={exportCSV}>↓ Export CSV</Button>} />
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card className="mb-5 !p-3.5">
          <div className="flex items-center gap-3 flex-wrap">
            <div className="text-[13px] font-semibold text-gray-600">Cohort Filter</div>
            <Select
              value={cohortId}
              onChange={e => setCohortId(e.target.value)}
              className="!mb-0 min-w-[220px] flex-1 max-w-[320px]"
            >
              <option value="">All Cohorts</option>
              {cohorts.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
            <span className="text-xs text-gray-400">
              {cohortId ? (cohorts.find(c => c.id === cohortId)?.name || 'Selected cohort') : 'All cohorts'} · {d.totalStudents || 0} students
            </span>
          </div>
        </Card>

        {loading && <Card className="mb-5"><Skeleton rows={3} height={26} /></Card>}

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
          <StatCard label="Total Students"    value={d.totalStudents    || 0} />
          <StatCard label="Present Today"     value={(d.presentToday || 0) + (d.lateToday || 0)} badge="incl. late" badgeColor="green" />
          <StatCard label="Absent Today"      value={d.absentToday      || 0} badgeColor="red" />
          <StatCard label="Late Today"        value={d.lateToday        || 0} badgeColor="yellow" />
          <StatCard label="Excused Today"     value={d.excusedToday     || 0} badge="approved" badgeColor="gray" />
          <StatCard label="Attendance Rate"   value={`${Math.round(d.schoolAttendanceRate || 0)}%`} progress={d.schoolAttendanceRate} />
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-5">
          <Card className="lg:col-span-2">
            <div className="font-semibold mb-4">Cohort Performance</div>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={chartData}>
                <XAxis dataKey="name" style={{ fontSize: 11 }} />
                <YAxis domain={[0,100]} style={{ fontSize: 11 }} />
                <Tooltip formatter={v => `${v}%`} />
                <Bar dataKey="rate" radius={[4,4,0,0]}>
                  {chartData.map((c, i) => (
                    <Cell key={i} fill={c.isSelected ? 'var(--red)' : (c.rate >= 75 ? '#22c55e' : c.rate >= 60 ? '#f59e0b' : '#C0392B')} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Card>
          <Card className="flex flex-col items-center justify-center">
            <div className="font-semibold mb-3 self-start">Today's Breakdown</div>
            <PieChart width={160} height={160}>
              <Pie data={pieData} cx={75} cy={75} innerRadius={45} outerRadius={70} dataKey="value">
                {pieData.map((e, i) => <Cell key={i} fill={e.color} />)}
              </Pie>
            </PieChart>
            <div className="flex gap-2.5 flex-wrap justify-center mt-2">
              {pieData.map(p => (
                <div key={p.name} className="flex items-center gap-1 text-[11px]">
                  <div className="w-2 h-2 rounded-full shrink-0" style={{ background: p.color }} />
                  {p.name}: {p.value}
                </div>
              ))}
            </div>
          </Card>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-5">
          <Card className="lg:col-span-2">
            <div className="font-semibold mb-1">Weekly Attendance Pattern</div>
            <p className="text-xs text-gray-400 mb-4">Status breakdown per weekday across all recorded days</p>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={weeklyData}>
                <XAxis dataKey="day" style={{ fontSize: 11 }} />
                <YAxis style={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="Present" stackId="w" fill="#22c55e" radius={[0,0,0,0]} />
                <Bar dataKey="Late" stackId="w" fill="#f59e0b" radius={[0,0,0,0]} />
                <Bar dataKey="Absent" stackId="w" fill="#C0392B" radius={[4,4,0,0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
          <Card>
            <div className="font-semibold mb-3">Student Behaviour Highlights</div>
            {behaviourList.length === 0 ? (
              <p className="text-xs text-gray-400 py-6 text-center">No behaviour data yet</p>
            ) : (
              <div className="space-y-2.5">
                {behaviourList.map(b => (
                  <div key={b.studentId} className="flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <div className="text-[12px] font-medium text-gray-700 truncate">{b.studentName}</div>
                      <div className="text-[10px] text-gray-400">{b.cohortName} · {b.totalRecords} records</div>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <span className="text-[10px] text-gray-400">{Math.round(b.attendanceRate)}%</span>
                      {tagBadge(b.behaviorTag)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        <Card>
          <div className="font-semibold mb-4">Recent Activity</div>
          {(d.recentActivity || []).map((a, i) => (
            <div key={i} className={`py-2 ${i < (d.recentActivity.length - 1) ? 'border-b border-gray-50' : ''}`}>
              <div className="text-[13px] font-medium">{a.action?.replace(/_/g, ' ')}</div>
              <div className="text-xs text-gray-400">{a.actor} — {a.detail}</div>
              <div className="text-[10px] font-mono text-gray-200 mt-0.5">{a.ts}</div>
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
  const [q, setQ]               = useState('')
  const [cohortFilter, setCohortFilter] = useState('')
  const [page, setPage]         = useState(0)
  const [size, setSize]         = useState(20)
  const [total, setTotal]       = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const debouncedQ = useDebounce(q, 350)

  const load = useCallback(() => {
    Promise.all([
      adminApi.searchStudents({
        q: debouncedQ,
        cohortId: cohortFilter || undefined,
        page,
        size,
        sort: 'name',
        order: 'asc',
      }),
      adminApi.listCohorts(),
    ]).then(([u, c]) => {
      setStudents(u.data.content); setTotal(u.data.totalElements); setTotalPages(Math.max(u.data.totalPages, 1))
      setCohorts(c.data)
    }).finally(() => setLoading(false))
  }, [debouncedQ, cohortFilter, page, size])
  useEffect(() => { setPage(0) }, [debouncedQ, cohortFilter])
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
      <PageHeader title="Students" subtitle={`${total} registered students`}
        actions={<Button onClick={() => { setModal('add'); setForm({ name: '', email: '', password: 'Student@1234', cohortId: cohorts[0]?.id || '' }) }}>+ Add Student</Button>} />
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card>
          <div className="flex gap-3 mb-4 flex-wrap">
            <Input
              placeholder="Search by name, email or registration number..."
              value={q}
              onChange={e => setQ(e.target.value)}
              className="!mb-0 flex-1 min-w-[220px]"
            />
            <Select value={cohortFilter} onChange={e => setCohortFilter(e.target.value)} className="!mb-0 min-w-[180px]">
              <option value="">All Cohorts</option>
              {cohorts.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </div>
          <Table
            columns={[
              { key: 'name',         label: 'Name',      strong: true },
              { key: 'email',        label: 'Email',     render: v => <span className="font-mono text-[11px] text-gray-400">{v}</span> },
              { key: 'cohortId',     label: 'Cohort',    render: (_, row) => cohorts.find(c => c.id === row.cohortId)?.name || '—' },
              { key: 'device',       label: 'Device Lock', render: (_, row) => row.device?.locked && row.device?.fingerprint ? <Badge status="PRESENT" label="Locked" /> : <Badge status="EXCUSED" label="Cleared" /> },
              { key: 'attendance',   label: 'Rate',      render: (_, row) => row.attendanceSummary ? `${Math.round(row.attendanceSummary.rate)}%` : '—' },
              { key: 'active',       label: 'Status',    render: v => <Badge status={v ? 'ACTIVE' : 'INACTIVE'} /> },
              { key: 'actions',      label: 'Actions',    render: (_, row) => (
                <div className="flex gap-1.5">
                  <Button size="sm" variant="outline" onClick={() => unlockDevice(row.id)}>Reset Device</Button>
                  <Button size="sm" variant="outline" onClick={() => setReset(row.id)}>Reset Pwd</Button>
                  <Button size="sm" variant="outline" onClick={() => toggleActive(row)}>{row.active ? 'Deactivate' : 'Activate'}</Button>
                </div>
              )},
            ]}
            rows={students}
          />
          <Pagination page={page} totalPages={totalPages} totalElements={total} size={size} onChange={(p, s) => { setPage(p); if (s) setSize(s) }} />
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
        <div className="flex gap-2 justify-end">
          <Button variant="outline" onClick={() => setModal(null)}>Cancel</Button>
          <Button loading={saving} onClick={addStudent}>Add Student</Button>
        </div>
      </Modal>

      {/* Reset Password Modal */}
      <Modal open={!!resetModal} onClose={() => setReset(null)} title="Reset Password">
        <Input label="New Password" type="password" value={resetPass} onChange={e => setResetPass(e.target.value)} placeholder="Min. 6 characters" />
        <div className="flex gap-2 justify-end">
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
        actions={<Button size="sm" onClick={() => { setModal(true); setForm({ name: '', email: '', password: 'Fac@1234', assignedCohortIds: [] }) }}>+ Add Facilitator</Button>} />
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card>
          <Table
            columns={[
              { key: 'name',              label: 'Name',    strong: true },
              { key: 'email',             label: 'Email',   render: v => <span className="font-mono text-[11px] text-gray-400">{v}</span> },
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
        <div className="mb-4">
          <label className="block text-xs font-medium text-gray-500 mb-2">Assigned Cohorts</label>
          {cohorts.length === 0 ? (
            <p className="text-xs text-gray-400">No cohorts available yet. Create cohorts first.</p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 max-h-[160px] overflow-y-auto">
              {cohorts.map(c => {
                const checked = (form.assignedCohortIds || []).includes(c.id)
                return (
                  <label key={c.id} className="flex items-center gap-2 cursor-pointer text-[13px] text-gray-600">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={e => {
                        const ids = form.assignedCohortIds || []
                        setForm(p => ({ ...p, assignedCohortIds: e.target.checked ? [...ids, c.id] : ids.filter(id => id !== c.id) }))
                      }}
                      className="w-4 h-4 accent-red"
                    />
                    {c.name}
                  </label>
                )
              })}
            </div>
          )}
        </div>
        <div className="flex gap-2 justify-end">
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
  const [viewCohort, setViewCohort] = useState(null)   // { id, name }
  const [members, setMembers]       = useState({ content: [], totalElements: 0, totalPages: 1 })
  const [memQ, setMemQ]             = useState('')
  const [memSort, setMemSort]       = useState('name')
  const [memOrder, setMemOrder]     = useState('asc')
  const [memPage, setMemPage]       = useState(0)
  const [memSize, setMemSize]       = useState(10)
  const [memLoading, setMemLoading] = useState(false)
  const debouncedMemQ = useDebounce(memQ, 350)

  const load = useCallback(() => {
    Promise.all([adminApi.listCohorts(), adminApi.listUsers('facilitator')]).then(([c, f]) => { setCohorts(c.data); setFacs(f.data) }).finally(() => setLoading(false))
  }, [])
  useEffect(() => { load() }, [load])

  const loadMembers = useCallback(() => {
    if (!viewCohort) return
    setMemLoading(true)
    adminApi.cohortStudentsPage(viewCohort.id, {
      q: debouncedMemQ || undefined,
      page: memPage,
      size: memSize,
      sort: memSort,
      order: memOrder,
    }).then(r => setMembers(r.data)).catch(() => toast.error('Failed to load students'))
      .finally(() => setMemLoading(false))
  }, [viewCohort, debouncedMemQ, memPage, memSize, memSort, memOrder])

  useEffect(() => { setMemPage(0) }, [debouncedMemQ, memSort, memOrder])
  useEffect(() => { loadMembers() }, [loadMembers])

  const toggleSort = (key) => {
    if (memSort === key) setMemOrder(o => (o === 'asc' ? 'desc' : 'asc'))
    else { setMemSort(key); setMemOrder('asc') }
  }
  const sortArrow = (key) => memSort === key ? (memOrder === 'asc' ? ' ▲' : ' ▼') : ''

  const add = async () => {
    if (!form.name || !form.facilitatorId) { toast.error('Fill all fields'); return }
    setSaving(true)
    try { await adminApi.createCohort(form); toast.success('Cohort created'); setModal(false); load() }
    catch (err) { toast.error(err.response?.data?.message || 'Failed') }
    finally { setSaving(false) }
  }

  const toggle = async id => { await adminApi.toggleCohort(id); load() }

  if (loading) return <LoadingPage />

  const sortableBtn = 'bg-transparent border-0 p-0 text-left font-[inherit] cursor-pointer hover:text-red'

  return (
    <>
      <PageHeader title="Cohorts" subtitle={`${cohorts.filter(c => c.active).length} active cohorts`}
        actions={<Button size="sm" onClick={() => { setModal(true); setForm({ name: '', facilitatorId: facilitators[0]?.id || '' }) }}>+ Add Cohort</Button>} />
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card>
          <Table
            columns={[
              { key: 'name',             label: 'Name',        strong: true },
              { key: 'facilitatorName',  label: 'Facilitator' },
              { key: 'studentCount',     label: 'Students' },
              { key: 'schedule',         label: 'Schedule',    render: v => <span className="text-xs font-mono text-gray-400">{v}</span> },
              { key: 'attendanceRate',   label: 'Att. Rate',   render: v => `${Math.round(v)}%` },
              { key: 'active',           label: 'Status',      render: v => <Badge status={v ? 'ACTIVE' : 'INACTIVE'} /> },
              { key: 'actions',          label: '',            render: (_, row) => (
                <div className="flex gap-1.5">
                  <Button size="sm" variant="outline" onClick={() => { setViewCohort({ id: row.id, name: row.name }); setMemQ(''); setMemPage(0) }}>View</Button>
                  <Button size="sm" variant="outline" onClick={() => toggle(row.id)}>{row.active ? 'Deactivate' : 'Activate'}</Button>
                </div>
              ) },
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
        <div className="flex gap-2 justify-end">
          <Button variant="outline" onClick={() => setModal(false)}>Cancel</Button>
          <Button loading={saving} onClick={add}>Create Cohort</Button>
        </div>
      </Modal>

      {/* Cohort members drill-down */}
      <Modal open={!!viewCohort} onClose={() => setViewCohort(null)} title={`${viewCohort?.name || ''} — Students`}>
        <Input
          placeholder="Search students..."
          value={memQ}
          onChange={e => setMemQ(e.target.value)}
          className="!mb-3"
        />
        {memLoading && <Skeleton rows={3} height={22} />}
        {!memLoading && (
          <>
            <Table
              columns={[
                { key: 'name', label: `Name${sortArrow('name')}`, strong: true, render: (_, row) => <button className={sortableBtn} onClick={() => toggleSort('name')}>{row.name}</button> },
                { key: 'email', label: 'Email', render: v => <span className="font-mono text-[11px] text-gray-400">{v}</span> },
                { key: 'registrationNumber', label: 'Reg. No.', render: v => <span className="font-mono text-[11px]">{v || '—'}</span> },
                { key: 'rate', label: `Rate${sortArrow('rate')}`, render: (_, row) => {
                  const r = Math.round(row.attendanceSummary?.rate ?? row.analytics?.attendanceRate ?? 0)
                  return <button className={sortableBtn} onClick={() => toggleSort('rate')}>{r ? `${r}%` : '—'}</button>
                } },
                { key: 'active', label: 'Status', render: v => <Badge status={v ? 'ACTIVE' : 'INACTIVE'} /> },
              ]}
              rows={members.content || []}
              emptyMessage="No students in this cohort"
            />
            <Pagination page={memPage} totalPages={Math.max(members.totalPages || 1, 1)} totalElements={members.totalElements || 0} size={memSize} onChange={(p, s) => { setMemPage(p); if (s) setMemSize(s) }} pageSizeOptions={[5, 10, 20]} />
          </>
        )}
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
      <div className="p-4 sm:p-6 animate-fade-in">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
          <StatCard label="Total Devices"  value={withDevices.length} />
          <StatCard label="Registered"     value={locked}   badgeColor="green" badge="Active" />
          <StatCard label="Unlocked"       value={unlocked} badgeColor="red"   badge="Needs action" color={unlocked > 0 ? 'var(--red)' : undefined} />
        </div>
        <Card>
          <Table
            columns={[
              { key: 'name',        label: 'Student',      strong: true },
              { key: 'device',      label: 'Fingerprint',  render: (v) => v?.fingerprint ? <span className="font-mono text-[11px]">{v.fingerprint}</span> : '—' },
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
const AUDIT_ACTIONS = ['LOGIN','LOGOUT','PASSWORD_RESET','QR_GENERATED','QR_EXPIRED','ATTENDANCE_MARKED','ATTENDANCE_MANUAL_OVERRIDE','DEVICE_REGISTERED','DEVICE_UNLOCKED','DEVICE_LOCKED','USER_CREATED','USER_UPDATED','USER_DEACTIVATED','COHORT_CREATED','COHORT_UPDATED','COHORT_TOGGLED','EXCUSE_SUBMITTED','EXCUSE_REVIEWED']

export function AdminAudit() {
  const [logs, setLogs]         = useState([])
  const [loading, setLoading]   = useState(true)
  const [action, setAction]     = useState('')
  const [actor, setActor]       = useState('')
  const [detail, setDetail]     = useState('')
  const [from, setFrom]         = useState('')
  const [to, setTo]             = useState('')
  const [page, setPage]         = useState(0)
  const [size, setSize]         = useState(50)
  const [total, setTotal]       = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [sort, setSort]         = useState('createdAt')
  const [order, setOrder]       = useState('desc')
  const debouncedActor = useDebounce(actor, 350)
  const debouncedDetail = useDebounce(detail, 350)

  const load = useCallback(() => {
    setLoading(true)
    adminApi.auditLogs({
      action: action || undefined,
      actorName: debouncedActor || undefined,
      detail: debouncedDetail || undefined,
      from: from || undefined,
      to: to || undefined,
      page,
      size,
      sort,
      order,
    }).then(r => {
      setLogs(r.data.content || [])
      setTotal(r.data.totalElements || 0)
      setTotalPages(Math.max(r.data.totalPages || 1, 1))
    }).catch(() => toast.error('Failed to load audit logs')).finally(() => setLoading(false))
  }, [action, debouncedActor, debouncedDetail, from, to, page, size, sort, order])
  useEffect(() => { setPage(0) }, [action, debouncedActor, debouncedDetail, from, to, size, sort, order])
  useEffect(() => { load() }, [load])

  const toggleSort = key => {
    if (sort === key) setOrder(o => o === 'asc' ? 'desc' : 'asc')
    else { setSort(key); setOrder('asc') }
  }
  const arrow = key => sort === key ? (order === 'asc' ? ' ▲' : ' ▼') : ''

  if (loading && logs.length === 0) return <LoadingPage />

  return (
    <>
      <PageHeader title="Audit Logs" subtitle="Full system activity trail" />
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card>
          <div className="flex gap-3 mb-4 flex-wrap">
            <Input placeholder="Search actor..." value={actor} onChange={e => setActor(e.target.value)} className="!mb-0 min-w-[160px] flex-1 max-w-[220px]" />
            <Select value={action} onChange={e => setAction(e.target.value)} className="!mb-0 min-w-[200px] flex-1 max-w-[240px]">
              <option value="">All Actions</option>
              {AUDIT_ACTIONS.map(a => <option key={a} value={a}>{a.replace(/_/g, ' ')}</option>)}
            </Select>
            <Input placeholder="Search details..." value={detail} onChange={e => setDetail(e.target.value)} className="!mb-0 min-w-[160px] flex-1 max-w-[220px]" />
            <Input type="date" value={from} onChange={e => setFrom(e.target.value)} className="!mb-0 w-[150px]" />
            <span className="self-center text-gray-400 text-xs">to</span>
            <Input type="date" value={to} onChange={e => setTo(e.target.value)} className="!mb-0 w-[150px]" />
          </div>
          {loading && <Skeleton rows={4} height={24} />}
          {!loading && (
            <>
              <Table
                columns={[
                  { key: 'action',    label: `Action${arrow('action')}`,    strong: true, render: (v) => <button className="bg-transparent border-0 p-0 text-left font-[inherit] cursor-pointer" onClick={() => toggleSort('action')}>{v?.replace(/_/g,' ')}</button> },
                  { key: 'actorName', label: `Actor${arrow('actorName')}`,  render: v => v || '—' },
                  { key: 'targetName',label: `Target${arrow('targetName')}`,render: v => v || '—' },
                  { key: 'detail',    label: 'Detail', render: v => <span className="text-xs text-gray-400">{v}</span> },
                  { key: 'createdAt', label: `Timestamp${arrow('createdAt')}`, render: v => v ? <button className="bg-transparent border-0 p-0 text-left font-mono text-[11px] text-gray-400 cursor-pointer" onClick={() => toggleSort('createdAt')}>{format(new Date(v), 'dd MMM HH:mm:ss')}</button> : '—' },
                ]}
                rows={logs}
              />
              <Pagination page={page} totalPages={totalPages} totalElements={total} size={size} onChange={(p, s) => { setPage(p); if (s) setSize(s) }} pageSizeOptions={[20, 50, 100]} />
            </>
          )}
        </Card>
      </div>
    </>
  )
}

// ── Analytics ────────────────────────────────────────────
export function AdminAnalytics() {
  const [data, setData]     = useState(null)
  const [students, setStudents] = useState([])
  const [cohorts, setCohorts] = useState([])
  const [cohortId, setCohortId] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    Promise.all([adminApi.schoolStats(cohortId || undefined), adminApi.listUsers('student'), adminApi.listCohorts()]).then(([s, u, c]) => { setData(s.data); setStudents(u.data); setCohorts(c.data) }).finally(() => setLoading(false))
  }, [cohortId])
  if (loading && !data) return <LoadingPage />
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
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card className="mb-5 !p-3.5">
          <div className="flex items-center gap-3 flex-wrap">
            <div className="text-[13px] font-semibold text-gray-600">Cohort Filter</div>
            <Select value={cohortId} onChange={e => setCohortId(e.target.value)} className="!mb-0 min-w-[220px] flex-1 max-w-[320px]">
              <option value="">All Cohorts</option>
              {cohorts.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
            {loading && <span className="text-xs text-gray-400">Refreshing…</span>}
          </div>
        </Card>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
          <StatCard label="School Rate"      value={`${Math.round(d.schoolAttendanceRate||0)}%`} progress={d.schoolAttendanceRate} />
          <StatCard label="Present Today"   value={d.presentToday||0} badgeColor="green" />
          <StatCard label="Late Today"      value={d.lateToday||0}    badgeColor="yellow" />
          <StatCard label="Excused Today"   value={d.excusedToday||0} badge="approved" badgeColor="gray" />
          <StatCard label="Absent Today"    value={d.absentToday||0}  badgeColor="red" />
          <StatCard label="Total Excused"   value={d.totalExcusedAllTime||0} badge="all-time" badgeColor="gray" />
        </div>

        {/* Day of Week Behaviour Patterns */}
        <Card className="mb-6">
          <div className="font-semibold mb-1">Day-of-Week Attendance Patterns</div>
          <p className="text-xs text-gray-400 mb-4">Breakdown of attendance statuses across days of the week</p>
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
          <div className="font-semibold mb-1">Student Behaviour & Punctuality Risk Analytics</div>
          <p className="text-xs text-gray-400 mb-4">Automated behavioral pattern classification derived from attendance & excuse history</p>
          <Table
            columns={[
              { key: 'studentName',        label: 'Student',  strong: true },
              { key: 'cohortName',         label: 'Cohort' },
              { key: 'present',            label: 'Present', render: v => <span className="text-green-dark font-medium">{v}</span> },
              { key: 'late',               label: 'Late',    render: v => <span className="text-yellow-dark font-medium">{v}</span> },
              { key: 'excused',            label: 'Excused', render: v => <span className="text-[#1d4ed8] font-medium">{v}</span> },
              { key: 'absent',             label: 'Absent',  render: v => <span className="text-red font-medium">{v}</span> },
              { key: 'attendanceRate',     label: 'Rate',    render: (v) => {
                const r = Math.round(v || 0)
                return (
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-xs">{r}%</span>
                    <div className="w-[50px] h-1 bg-gray-100 rounded-[2px]">
                      <div className="h-full rounded-[2px]" style={{ width: `${r}%`, background: r>=80?'var(--green)':r>=60?'#f59e0b':'var(--red)' }} />
                    </div>
                  </div>
                )
              }},
              { key: 'behaviorTag',        label: 'Behavior Pattern', render: (v) => renderTagBadge(v) },
              { key: 'behaviorInsightText',label: 'Behavioral Observation', render: v => <span className="text-xs text-gray-600">{v}</span> },
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
      <div className="p-4 sm:p-6 max-w-[600px] animate-fade-in">
        <Card className="mb-4">
          <div className="font-semibold mb-3">Account</div>
          <div className="text-[13px] text-gray-600">Logged in as <strong>{user?.name}</strong></div>
          <div className="text-xs text-gray-400 font-mono">{user?.email}</div>
        </Card>

        <Card className="mb-4">
          <div className="font-semibold mb-4">Network & Attendance Settings</div>
          <p className="text-xs text-gray-400 mb-4">
            Configure the school network and attendance time windows. Changes take effect immediately.
          </p>
          {netLoading ? (
            <div className="text-center py-5"><div className="mx-auto inline-block h-5 w-5 rounded-full border-2 border-gray-200 border-t-red animate-spin" /></div>
          ) : netSettings && (
            <>
              <div className="font-medium text-[13px] mb-2 text-gray-600">Network</div>
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
              <div className="mb-3.5">
                <label className="flex items-center gap-2 cursor-pointer text-[13px] text-gray-600">
                  <input
                    type="checkbox"
                    checked={netSettings.network_enforce === 'true'}
                    onChange={e => updateNet('network_enforce', e.target.checked ? 'true' : 'false')}
                    className="w-4 h-4 accent-red"
                  />
                  Enforce school network for attendance
                </label>
                <p className="text-[11px] text-gray-400 mt-1 ml-6">
                  When enabled, students must be connected to the school network to mark attendance
                </p>
              </div>
              <div className="h-px bg-gray-100 my-4" />
              <div className="font-medium text-[13px] mb-2 text-gray-600">GPS Geofence Fallback</div>
              <div className="mb-2.5">
                <label className="flex items-center gap-2 cursor-pointer text-[13px] text-gray-600">
                  <input
                    type="checkbox"
                    checked={netSettings.geofence_fallback_enabled === 'true'}
                    onChange={e => updateNet('geofence_fallback_enabled', e.target.checked ? 'true' : 'false')}
                    className="w-4 h-4 accent-red"
                  />
                  Enable GPS Geofence Fallback when school network is offline/disconnected
                </label>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
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
              <div className="h-px bg-gray-100 my-4" />
              <div className="font-medium text-[13px] mb-2 text-gray-600">Attendance Time Windows</div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
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
              <Button onClick={saveNetwork} loading={netSaving} className="mt-1">
                Save Network & Geofence Settings
              </Button>
            </>
          )}
        </Card>

        <Card>
          <div className="font-semibold mb-4">Change Password</div>
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
