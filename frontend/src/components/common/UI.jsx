import React, { useEffect, useRef, useState } from 'react'

/* ── Button ─────────────────────────────────────────── */
export function Button({ children, variant = 'primary', size = 'md', loading, className = '', ...props }) {
  const base = 'inline-flex items-center justify-center gap-1.5 font-medium rounded-lg transition-all duration-150 disabled:opacity-60 disabled:cursor-not-allowed'
  const variants = {
    primary:  'bg-red text-white hover:bg-red-dark active:scale-95',
    outline:  'bg-white text-gray-600 border border-gray-200 hover:border-gray-400 hover:text-gray-900 active:scale-95',
    ghost:    'bg-transparent text-gray-600 hover:bg-gray-50 hover:text-gray-900',
    danger:   'bg-red text-white hover:bg-red-dark active:scale-95',
  }
  const sizes = { sm: 'px-3 py-1.5 text-xs', md: 'px-4 py-2 text-sm', lg: 'px-5 py-2.5 text-base' }

  return (
    <button
      className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}
      disabled={loading || props.disabled}
      {...props}
    >
      {loading && <span className="inline-block h-3.5 w-3.5 rounded-full border-2 border-gray-200 border-t-red animate-spin" />}
      {children}
    </button>
  )
}

/* ── Card ───────────────────────────────────────────── */
export function Card({ children, className = '', style = {}, ...props }) {
  return (
    <div className={`bg-white border border-gray-100 rounded-lg p-5 shadow ${className}`} style={style} {...props}>
      {children}
    </div>
  )
}

/* ── StatCard ───────────────────────────────────────── */
export function StatCard({ label, value, sub, badge, badgeColor = 'gray', progress, color }) {
  const badgeColors = {
    green:  'bg-green-light text-green-dark border-[#a8dbb8]',
    red:    'bg-red-light text-red border-red-mid',
    yellow: 'bg-yellow-light text-yellow-dark border-[#f3dfa8]',
    gray:   'bg-gray-50 text-gray-600 border-gray-200',
  }
  const bc = badgeColors[badgeColor] || badgeColors.gray
  return (
    <Card>
      <div className="text-[11px] font-medium text-gray-400 uppercase tracking-wide mb-2">
        {label}
      </div>
      <div className="text-[28px] font-semibold leading-none" style={{ color: color || 'var(--gray-900)' }}>
        {value}
      </div>
      {sub && <div className="text-[11px] text-gray-400 mt-1">{sub}</div>}
      {progress !== undefined && (
        <div className="h-[5px] bg-gray-100 rounded-[3px] mt-2.5 overflow-hidden">
          <div className="h-full rounded-[3px] transition-[width] duration-400"
            style={{
              width: `${Math.min(100, progress)}%`,
              background: progress >= 80 ? 'var(--green)' : progress >= 60 ? '#f59e0b' : 'var(--red)',
            }} />
        </div>
      )}
      {badge && (
        <span className={`inline-block text-[10px] px-1.5 py-0.5 rounded-full mt-1.5 font-medium border ${bc}`}>
          {badge}
        </span>
      )}
    </Card>
  )
}

/* ── Input ──────────────────────────────────────────── */
const inputClass = (error) => `w-full px-3.5 py-2.5 text-sm text-gray-900 bg-white border-[1.5px] rounded-md transition-colors outline-none focus:border-red placeholder:text-gray-400 ${error ? 'border-red' : 'border-gray-200'}`

export function Input({ label, error, className = '', ...props }) {
  return (
    <div className="mb-3.5">
      {label && <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>}
      <input className={`${inputClass(error)} ${className}`} {...props} />
      {error && <p className="text-[11px] text-red mt-0.5">{error}</p>}
    </div>
  )
}

/* ── Select ─────────────────────────────────────────── */
export function Select({ label, children, error, className = '', ...props }) {
  return (
    <div className="mb-3.5">
      {label && <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>}
      <select className={`${inputClass(error)} ${className}`} {...props}>
        {children}
      </select>
      {error && <p className="text-[11px] text-red mt-0.5">{error}</p>}
    </div>
  )
}

/* ── Textarea ───────────────────────────────────────── */
export function Textarea({ label, error, className = '', ...props }) {
  return (
    <div className="mb-3.5">
      {label && <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>}
      <textarea className={`${inputClass(error)} resize-y min-h-20 ${className}`} {...props} />
      {error && <p className="text-[11px] text-red mt-0.5">{error}</p>}
    </div>
  )
}

/* ── Modal ──────────────────────────────────────────── */
export function Modal({ open, onClose, title, children }) {
  if (!open) return null
  return (
    <div onClick={onClose} className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div onClick={e => e.stopPropagation()} className="bg-white rounded-lg p-6 w-full max-w-[440px] shadow-md animate-fade-in max-h-[90vh] overflow-y-auto">
        {title && <h2 className="text-base font-semibold mb-4">{title}</h2>}
        {children}
      </div>
    </div>
  )
}

/* ── Alert ──────────────────────────────────────────── */
export function Alert({ type = 'info', children }) {
  const styles = {
    info:    'bg-blue-light text-blue-dark border-[#bfdbfe]',
    success: 'bg-green-light text-green-dark border-[#a8dbb8]',
    warning: 'bg-yellow-light text-yellow-dark border-[#f3dfa8]',
    error:   'bg-red-light text-red border-red-mid',
  }
  return (
    <div className={`${styles[type] || styles.info} border rounded-md px-4 py-3 text-[13px] mb-4 leading-relaxed`}>
      {children}
    </div>
  )
}

/* ── Badge ──────────────────────────────────────────── */
export function Badge({ status, label }) {
  const map = {
    PRESENT:  'bg-green-light text-green-dark border-[#a8dbb8]',
    LATE:     'bg-yellow-light text-yellow-dark border-[#f3dfa8]',
    ABSENT:   'bg-red-light text-red border-red-mid',
    EXCUSED:  'bg-[#f0f4ff] text-[#1d4ed8] border-[#bfdbfe]',
    ACCEPTED: 'bg-green-light text-green-dark border-[#a8dbb8]',
    APPROVED: 'bg-green-light text-green-dark border-[#a8dbb8]',
    REJECTED: 'bg-red-light text-red border-red-mid',
    PENDING:  'bg-yellow-light text-yellow-dark border-[#f3dfa8]',
    ACTIVE:   'bg-green-light text-green-dark border-[#a8dbb8]',
    INACTIVE: 'bg-gray-50 text-gray-600 border-gray-200',
    MANUAL:   'bg-[#faf5ff] text-[#7c3aed] border-[#ddd6fe]',
    HOLIDAY:  'bg-[#fef3c7] text-[#92400e] border-[#fde68a]',
  }
  const s = map[status?.toUpperCase()] || 'bg-gray-50 text-gray-600 border-gray-200'
  return (
    <span className={`inline-block text-[11px] font-medium px-2 py-0.5 rounded-full border ${s}`}>
      {label || map[status?.toUpperCase()]?.label || (status || '—')}
    </span>
  )
}

/* ── Table ──────────────────────────────────────────── */
export function Table({ columns, rows, emptyMessage = 'No records found' }) {
  return (
    <div className="overflow-x-auto rounded-md border border-gray-100 bg-white">
      <table className="w-full border-collapse">
        <thead>
          <tr>
            {columns.map(col => (
              <th key={col.key} className="text-[11px] font-medium text-gray-400 uppercase tracking-widest px-3.5 py-2.5 text-left bg-gray-50 border-b border-gray-100 whitespace-nowrap">
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr><td colSpan={columns.length} className="py-10 text-center text-gray-400 text-[13px]">{emptyMessage}</td></tr>
          ) : rows.map((row, i) => (
            <tr key={i} className={`hover:bg-gray-50 ${i < rows.length - 1 ? 'border-b border-gray-50' : ''}`}>
              {columns.map(col => (
                <td key={col.key} className={`px-3.5 py-2.5 text-[13px] ${col.strong ? 'text-gray-900 font-medium' : 'text-gray-600'}`}>
                  {col.render ? col.render(row[col.key], row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* ── PageHeader ─────────────────────────────────────── */
export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="px-4 sm:px-7 pt-6 border-b border-gray-100 bg-white sticky top-11 z-10">
      <div className="flex items-start justify-between gap-3 pb-4 flex-wrap">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold text-gray-900">{title}</h1>
          {subtitle && <p className="text-[13px] text-gray-400 mt-0.5">{subtitle}</p>}
        </div>
        {actions && <div className="flex gap-2 items-center flex-wrap">{actions}</div>}
      </div>
    </div>
  )
}

/* ── Loading spinner page ───────────────────────────── */
export function LoadingPage() {
  return (
    <div className="flex items-center justify-center min-h-[60vh] flex-col gap-3">
      <div className="inline-block h-9 w-9 rounded-full border-[3px] border-gray-200 border-t-red animate-spin" />
      <p className="text-gray-400 text-[13px]">Loading...</p>
    </div>
  )
}

/* ── Empty state ────────────────────────────────────── */
export function Empty({ message = 'Nothing here yet' }) {
  return (
    <div className="text-center px-5 py-12 text-gray-400">
      <div className="text-4xl mb-3">📭</div>
      <p className="text-[13px]">{message}</p>
    </div>
  )
}

/* ── Skeleton loading rows ──────────────────────────── */
export function Skeleton({ rows = 4, height = 18 }) {
  return (
    <div>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="animate-pulse bg-gray-100 rounded" style={{ height, marginBottom: 10 }} />
      ))}
    </div>
  )
}

/* ── Pagination ─────────────────────────────────────── */
export function Pagination({ page, totalPages, totalElements, size, onChange, pageSizeOptions = [10, 20, 50, 100] }) {
  if (totalPages <= 1 && totalElements <= size) return null
  const pages = Array.from({ length: Math.max(1, totalPages) }, (_, i) => i)
  const visible = pages.length <= 7 ? pages : page <= 3 ? pages.slice(0, 5).concat(-1, pages.slice(-1)) : page >= totalPages - 3 ? [pages[0], -1].concat(pages.slice(-5)) : [pages[0], -1].concat(pages.slice(page - 1, page + 2), -1, pages.slice(-1))

  const navBtn = (disabled) => `px-2.5 py-1 text-xs rounded border border-gray-200 bg-white transition-colors ${disabled ? 'text-gray-300 cursor-not-allowed' : 'text-gray-600 hover:border-gray-400 cursor-pointer'}`
  return (
    <div className="flex items-center justify-between gap-2.5 flex-wrap mt-3.5">
      <span className="text-xs text-gray-400">
        {totalElements === 0 ? 'No results' : `Showing ${page * size + 1}–${Math.min((page + 1) * size, totalElements)} of ${totalElements}`}
      </span>
      <div className="flex items-center gap-1">
        <select
          value={size}
          onChange={e => onChange(0, Number(e.target.value))}
          className="text-xs px-1.5 py-1 rounded border border-gray-200 bg-white text-gray-600"
        >
          {pageSizeOptions.map(s => <option key={s} value={s}>{s} / page</option>)}
        </select>
        <button onClick={() => onChange(page - 1, size)} disabled={page === 0} className={navBtn(page === 0)}>
          Prev
        </button>
        {visible.map((p, i) => p === -1
          ? <span key={`e${i}`} className="text-xs text-gray-400 px-1">…</span>
          : <button key={p} onClick={() => onChange(p, size)}
              className={`px-2.5 py-1 text-xs rounded border border-gray-200 cursor-pointer ${p === page ? 'bg-red text-white border-red font-semibold' : 'bg-white text-gray-600 hover:border-gray-400'}`}>
              {p + 1}
            </button>)}
        <button onClick={() => onChange(page + 1, size)} disabled={page >= totalPages - 1} className={navBtn(page >= totalPages - 1)}>
          Next
        </button>
      </div>
    </div>
  )
}

/* ── Rating chip (EXCELLENT/GOOD/FAIR/POOR) ─────────── */
export function RatingChip({ rating }) {
  const map = {
    EXCELLENT: 'bg-green-light text-green-dark border-[#a8dbb8]',
    GOOD:      'bg-[#eff6ff] text-[#1d4ed8] border-[#bfdbfe]',
    FAIR:      'bg-yellow-light text-yellow-dark border-[#f3dfa8]',
    POOR:      'bg-red-light text-red border-red-mid',
  }
  const s = map[rating] || 'bg-gray-50 text-gray-600 border-gray-200'
  return (
    <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded-full border uppercase tracking-wide ${s}`}>
      {rating || '—'}
    </span>
  )
}

/* ── Debounced value hook ───────────────────────────── */
export function useDebounce(value, delay = 350) {
  const [debounced, setDebounced] = useState(value)
  const timer = useRef()
  useEffect(() => {
    timer.current = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer.current)
  }, [value, delay])
  return debounced
}
