import React, { useState, useEffect, useCallback } from 'react'
import { adminApi, downloadBlob } from '../../api/client'
import { Card, Badge, Table, PageHeader, LoadingPage, Alert, Button, Modal, Input, Select, Skeleton } from '../../components/common/UI'
import toast from 'react-hot-toast'
import { format, addMonths, subMonths, startOfMonth, endOfMonth, getDay, eachDayOfInterval, parseISO, isSameMonth } from 'date-fns'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

const RANGE_PRESETS = [
  { key: 'today',     label: 'Today' },
  { key: 'yesterday', label: 'Yesterday' },
  { key: 'last7',     label: 'Last 7 Days' },
  { key: 'last30',    label: 'Last 30 Days' },
  { key: 'custom',    label: 'Custom' },
]

function resolveRange(preset, customStart, customEnd) {
  const today = new Date()
  switch (preset) {
    case 'yesterday': return { start: format(addMonths(today, 0) && new Date(today.getTime() - 86400000), 'yyyy-MM-dd'), end: format(new Date(today.getTime() - 86400000), 'yyyy-MM-dd') }
    case 'last7':     return { start: format(new Date(today.getTime() - 6 * 86400000), 'yyyy-MM-dd'), end: format(today, 'yyyy-MM-dd') }
    case 'last30':    return { start: format(new Date(today.getTime() - 29 * 86400000), 'yyyy-MM-dd'), end: format(today, 'yyyy-MM-dd') }
    case 'custom':    return { start: customStart || format(today, 'yyyy-MM-dd'), end: customEnd || format(today, 'yyyy-MM-dd') }
    default:          return { start: format(today, 'yyyy-MM-dd'), end: format(today, 'yyyy-MM-dd') }
  }
}

const calCellBase = 'min-h-[56px] sm:min-h-[62px] rounded-[8px] bg-gray-50 border border-transparent p-1.5 text-left flex flex-col gap-0.5 text-xs text-gray-900 hover:border-red-mid cursor-pointer'

const calDot = 'inline-block h-2 w-2 rounded-full shrink-0'

export function AdminCalendar() {
  const [ym, setYm]                 = useState(() => { const t = new Date(); return { year: t.getFullYear(), month: t.getMonth() + 1 } })
  const [cohortId, setCohortId]     = useState('')
  const [cohorts, setCohorts]       = useState([])
  const [cal, setCal]               = useState(null)
  const [loading, setLoading]       = useState(true)
  const [holidays, setHolidays]     = useState([])
  const [selectedDay, setSelectedDay] = useState(format(new Date(), 'yyyy-MM-dd'))
  const [dayRecords, setDayRecords] = useState({ content: [], totalElements: 0 })
  const [dayLoading, setDayLoading] = useState(false)

  const [preset, setPreset]         = useState('last7')
  const [customStart, setCustomStart] = useState(format(new Date(), 'yyyy-MM-dd'))
  const [customEnd, setCustomEnd]     = useState(format(new Date(), 'yyyy-MM-dd'))

  const [holModal, setHolModal]     = useState(null) // { holiday?, form }
  const [holSaving, setHolSaving]   = useState(false)

  const loadHolidays = useCallback(() => {
    adminApi.listHolidays().then(r => setHolidays(r.data || [])).catch(() => {})
  }, [])
  useEffect(() => { loadHolidays(); adminApi.listCohorts().then(r => setCohorts(r.data || [])).catch(() => {}) }, [loadHolidays])

  useEffect(() => {
    let alive = true
    setLoading(true)
    adminApi.calendarMonth({ year: ym.year, month: ym.month, cohortId: cohortId || undefined })
      .then(r => { if (alive) setCal(r.data) })
      .catch(() => alive && toast.error('Failed to load calendar'))
      .finally(() => alive && setLoading(false))
    return () => { alive = false }
  }, [ym, cohortId])

  useEffect(() => {
    if (!selectedDay) return
    setDayLoading(true)
    adminApi.searchAttendance({ cohortId: cohortId || undefined, start: selectedDay, end: selectedDay })
      .then(r => setDayRecords(r.data))
      .catch(() => {})
      .finally(() => setDayLoading(false))
  }, [selectedDay, cohortId])

  const monthNav = delta => {
    const next = delta < 0 ? subMonths(new Date(ym.year, ym.month - 1), 1) : addMonths(new Date(ym.year, ym.month - 1), 1)
    setYm({ year: next.getFullYear(), month: next.getMonth() + 1 })
    setSelectedDay(format(next, 'yyyy-MM-dd'))
  }

  const range = resolveRange(preset, customStart, customEnd)

  const doExport = async () => {
    try {
      const res = await adminApi.exportAttendance({ cohortId: cohortId || undefined, start: range.start, end: range.end, format: 'xlsx' })
      downloadBlob(res, `attendance_${range.start}_to_${range.end}.xlsx`)
      toast.success('Attendance exported')
    } catch { toast.error('Export failed') }
  }

  const saveHoliday = async () => {
    const f = holModal.form
    if (!f.name || !f.startDate) { toast.error('Name and start date required'); return }
    setHolSaving(true)
    try {
      const body = { ...f, appliesToAll: f.appliesToAll === true || f.appliesToAll === 'true' }
      if (holModal.holiday) await adminApi.updateHoliday(holModal.holiday.id, body)
      else await adminApi.createHoliday(body)
      toast.success(holModal.holiday ? 'Holiday updated' : 'Holiday created')
      setHolModal(null); loadHolidays()
    } catch (err) { toast.error(err.response?.data?.message || 'Failed') }
    finally { setHolSaving(false) }
  }

  const toggleHoliday = async h => { try { await adminApi.toggleHoliday(h.id); toast.success(h.active ? 'Holiday deactivated' : 'Holiday activated'); loadHolidays() } catch { toast.error('Failed') } }
  const deleteHoliday = async h => { try { await adminApi.deleteHoliday(h.id); toast.success('Holiday deleted'); loadHolidays() } catch { toast.error('Failed') } }

  const dayMap = {}
  ;(cal?.days || []).forEach(d => { dayMap[d.date] = d })

  const monthStart = startOfMonth(new Date(ym.year, ym.month - 1, 1))
  const monthEnd = endOfMonth(monthStart)
  const cells = []
  const lead = (getDay(monthStart) + 6) % 7
  for (let i = 0; i < lead; i++) cells.push(null)
  eachDayOfInterval({ start: monthStart, end: monthEnd }).forEach(d => cells.push(d))

  const statusBadge = d => {
    if (!d) return <span className={calDot} style={{ background: 'transparent' }} />
    const n = (d.present || 0) + (d.late || 0) + (d.excused || 0) + (d.absent || 0)
    if (d.holiday) return <span className={calDot} style={{ background: 'var(--gray-400)' }} title={`Holiday: ${d.holidayName}`} />
    if (n === 0) return <span className={calDot} style={{ background: 'transparent' }} />
    const pct = ((d.present || 0) + (d.late || 0)) / n
    return <span className={calDot} style={{ background: pct >= 0.75 ? 'var(--green)' : pct >= 0.5 ? '#f59e0b' : 'var(--red)' }} />
  }

  if (loading && !cal) return <LoadingPage />

  return (
    <>
      <PageHeader title="Attendance Calendar" subtitle="Month overview, holidays, and day-by-day attendance"
        actions={
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => { setHolModal({ holiday: null, form: { name: '', startDate: format(new Date(), 'yyyy-MM-dd'), endDate: format(new Date(), 'yyyy-MM-dd'), reason: '', appliesToAll: true, cohortId: '' } }) }}>+ Add Holiday</Button>
            <Button size="sm" onClick={doExport}>↓ Export Range</Button>
          </div>
        } />
      <div className="p-4 sm:p-6 animate-fade-in">
        <Card className="mb-4">
          <div className="flex items-center gap-3 flex-wrap">
            <Button variant="outline" size="sm" onClick={() => monthNav(-1)}>←</Button>
            <div className="font-bold min-w-[180px] text-center capitalize">
              {format(new Date(ym.year, ym.month - 1, 1), 'MMMM yyyy')}
            </div>
            <Button variant="outline" size="sm" onClick={() => monthNav(1)}>→</Button>
            <Select value={cohortId} onChange={e => setCohortId(e.target.value)} className="!mb-0 min-w-[180px] lg:ml-auto">
              <option value="">All Cohorts</option>
              {cohorts.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </div>
          {loading && <Skeleton rows={4} height={40} />}
        </Card>

        <div className="flex gap-2 mb-4 flex-wrap items-center">
          {RANGE_PRESETS.map(r => (
            <button
              key={r.key}
              className={`px-3.5 py-1.5 rounded-full bg-white border border-gray-100 text-gray-500 text-xs font-medium transition-colors hover:border-red-mid hover:text-red cursor-pointer ${preset === r.key ? 'bg-red border-red text-white hover:text-white' : ''}`}
              onClick={() => setPreset(r.key)}
            >{r.label}</button>
          ))}
          {preset === 'custom' && (
            <>
              <Input type="date" value={customStart} onChange={e => setCustomStart(e.target.value)} className="!mb-0 w-[150px]" />
              <span className="text-gray-400 text-xs">to</span>
              <Input type="date" value={customEnd} onChange={e => setCustomEnd(e.target.value)} className="!mb-0 w-[150px]" />
            </>
          )}
          <span className="lg:ml-auto text-xs text-gray-400 font-mono">
            {range.start} → {range.end}
          </span>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-4">
          <Card className="lg:col-span-2">
            <div className="grid grid-cols-7 gap-1">
              {WEEKDAYS.map(w => <div key={w} className="text-[11px] font-semibold text-gray-400 text-center py-1">{w}</div>)}
              {cells.map((d, i) => {
                if (!d) return <div key={`b${i}`} className="min-h-[56px] sm:min-h-[62px] rounded-[8px] bg-transparent" />
                const iso = format(d, 'yyyy-MM-dd')
                const info = dayMap[iso]
                const selected = selectedDay === iso
                const today = format(new Date(), 'yyyy-MM-dd') === iso
                return (
                  <button
                    key={iso}
                    className={`${calCellBase} ${selected ? 'bg-red-light border-red' : ''} ${today ? 'border-red-mid' : ''} ${info?.holiday ? 'bg-blue-light' : ''} ${info?.weekend ? 'opacity-55' : ''}`}
                    onClick={() => setSelectedDay(iso)}
                  >
                    <div className="flex justify-between items-center gap-1">
                      <span className={today ? 'font-bold' : 'font-medium'}>{format(d, 'd')}</span>
                      {statusBadge(info)}
                    </div>
                    {info && (
                      <div className="flex gap-1 text-[10px] font-semibold font-mono">
                        <span className="text-green-dark">{info.present || 0}</span>
                        <span className="text-yellow-dark">{info.late || 0}</span>
                        <span className="text-[#1d4ed8]">{info.excused || 0}</span>
                        <span className="text-red">{info.absent || 0}</span>
                      </div>
                    )}
                    {info?.holiday && <div className="text-[9px] text-blue-dark whitespace-nowrap overflow-hidden text-ellipsis w-full">{info.holidayName}</div>}
                  </button>
                )
              })}
            </div>
            <div className="flex gap-3.5 mt-3 text-[11px] text-gray-400 flex-wrap">
              <span className="flex items-center gap-1"><span className={calDot} style={{ background: 'var(--green)' }} /> Present</span>
              <span className="flex items-center gap-1"><span className={calDot} style={{ background: '#f59e0b' }} /> Late</span>
              <span className="flex items-center gap-1"><span className={calDot} style={{ background: '#1d4ed8' }} /> Excused</span>
              <span className="flex items-center gap-1"><span className={calDot} style={{ background: 'var(--red)' }} /> Absent</span>
              <span className="flex items-center gap-1"><span className={calDot} style={{ background: 'var(--gray-400)' }} /> Holiday</span>
            </div>
          </Card>

          <Card>
            <div className="font-semibold mb-1">Selected Day</div>
            <div className="text-xs text-gray-400 mb-3">
              {selectedDay ? format(parseISO(selectedDay), 'EEEE, dd MMM yyyy') : 'Select a day'}
            </div>
            {selectedDay && dayMap[selectedDay] && (
              <div className="grid grid-cols-2 gap-2 mb-3">
                <StatBox label="Present" value={dayMap[selectedDay].present || 0} color="var(--green-dark)" />
                <StatBox label="Late" value={dayMap[selectedDay].late || 0} color="var(--yellow-dark)" />
                <StatBox label="Excused" value={dayMap[selectedDay].excused || 0} color="#1d4ed8" />
                <StatBox label="Absent" value={dayMap[selectedDay].absent || 0} color="var(--red)" />
              </div>
            )}
            {selectedDay && dayMap[selectedDay]?.holiday && (
              <Alert type="info">Holiday — {dayMap[selectedDay].holidayName}</Alert>
            )}
            <div className="font-semibold mb-2 mt-2">Records</div>
            {dayLoading ? <Skeleton rows={3} height={20} /> : (
              <div className="max-h-[300px] overflow-y-auto">
                <Table
                  columns={[
                    { key: 'studentName', label: 'Student', strong: true },
                    { key: 'status', label: 'Status', render: v => <Badge status={v} /> },
                  ]}
                  rows={dayRecords.content || []}
                  emptyMessage="No records for this day"
                />
              </div>
            )}
          </Card>
        </div>

        <Card>
          <div className="font-semibold mb-1">Holidays</div>
          <p className="text-xs text-gray-400 mb-4">School holidays and special days — attendance is auto-marked as Holiday</p>
          <Table
            columns={[
              { key: 'name', label: 'Name', strong: true },
              { key: 'startDate', label: 'Start', render: v => format(parseISO(v), 'dd MMM yyyy') },
              { key: 'endDate', label: 'End', render: v => format(parseISO(v), 'dd MMM yyyy') },
              { key: 'scope', label: 'Scope', render: (_, row) => row.appliesToAll ? 'All Cohorts' : (cohorts.find(c => c.id === row.cohortId)?.name || 'Specific') },
              { key: 'reason', label: 'Reason', render: v => <span className="text-xs text-gray-400">{v || '—'}</span> },
              { key: 'active', label: 'Status', render: v => <Badge status={v ? 'ACTIVE' : 'INACTIVE'} /> },
              { key: 'actions', label: '', render: (_, row) => (
                <div className="flex gap-1.5">
                  <Button size="sm" variant="outline" onClick={() => setHolModal({ holiday: row, form: { name: row.name, startDate: row.startDate, endDate: row.endDate, reason: row.reason || '', appliesToAll: row.appliesToAll, cohortId: row.cohortId || '' } })}>Edit</Button>
                  <Button size="sm" variant="outline" onClick={() => toggleHoliday(row)}>{row.active ? 'Deactivate' : 'Activate'}</Button>
                  <Button size="sm" variant="outline" onClick={() => deleteHoliday(row)}>Delete</Button>
                </div>
              ) },
            ]}
            rows={holidays}
            emptyMessage="No holidays configured"
          />
        </Card>
      </div>

      <Modal open={!!holModal} onClose={() => setHolModal(null)} title={holModal?.holiday ? 'Edit Holiday' : 'Add Holiday'}>
        {holModal && (
          <>
            <Input label="Holiday Name *" value={holModal.form.name} onChange={e => setHolModal(p => ({ ...p, form: { ...p.form, name: e.target.value } }))} placeholder="e.g. Independence Day" />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input label="Start Date *" type="date" value={holModal.form.startDate} onChange={e => setHolModal(p => ({ ...p, form: { ...p.form, startDate: e.target.value } }))} />
              <Input label="End Date *" type="date" value={holModal.form.endDate} onChange={e => setHolModal(p => ({ ...p, form: { ...p.form, endDate: e.target.value } }))} />
            </div>
            <Input label="Reason / Note" value={holModal.form.reason} onChange={e => setHolModal(p => ({ ...p, form: { ...p.form, reason: e.target.value } }))} placeholder="Optional" />
            <Select label="Scope" value={holModal.form.appliesToAll === true || holModal.form.appliesToAll === 'true' ? 'all' : 'cohort'} onChange={e => setHolModal(p => ({ ...p, form: { ...p.form, appliesToAll: e.target.value === 'all' } }))}>
              <option value="all">All Cohorts</option>
              <option value="cohort">Specific Cohort</option>
            </Select>
            {(holModal.form.appliesToAll === false || holModal.form.appliesToAll === 'false') && (
              <Select label="Cohort" value={holModal.form.cohortId} onChange={e => setHolModal(p => ({ ...p, form: { ...p.form, cohortId: e.target.value } }))}>
                {cohorts.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </Select>
            )}
            <div className="flex gap-2 justify-end">
              <Button variant="outline" onClick={() => setHolModal(null)}>Cancel</Button>
              <Button loading={holSaving} onClick={saveHoliday}>{holModal.holiday ? 'Update' : 'Create'}</Button>
            </div>
          </>
        )}
      </Modal>
    </>
  )
}

function StatBox({ label, value, color }) {
  return (
    <div className="bg-gray-50 rounded p-2.5 text-center">
      <div className="text-xl font-bold" style={{ color }}>{value}</div>
      <div className="text-[11px] text-gray-400 mt-0.5">{label}</div>
    </div>
  )
}
