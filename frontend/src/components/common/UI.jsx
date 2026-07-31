import React, { useEffect, useRef, useState } from 'react'

/* ── Button ─────────────────────────────────────────── */
export function Button({ children, variant = 'primary', size = 'md', loading, className = '', ...props }) {
  const base = 'inline-flex items-center gap-2 font-medium rounded-lg transition-all duration-150 disabled:opacity-60 disabled:cursor-not-allowed'
  const variants = {
    primary:  'bg-red text-white hover:bg-red-dark active:scale-95',
    outline:  'bg-white text-gray-600 border border-gray-200 hover:border-gray-400 hover:text-gray-900',
    ghost:    'bg-transparent text-gray-600 hover:bg-gray-50 hover:text-gray-900',
    danger:   'bg-red text-white hover:bg-red-dark',
  }
  const sizes = { sm: 'px-3 py-1.5 text-xs', md: 'px-4 py-2 text-sm', lg: 'px-5 py-2.5 text-base' }

  return (
    <button
      className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}
      style={{
        background: variant === 'primary' || variant === 'danger' ? 'var(--red)' : undefined,
        color: variant === 'primary' || variant === 'danger' ? '#fff' : undefined,
        border: variant === 'outline' ? '1px solid var(--gray-200)' : 'none',
        borderRadius: 'var(--radius)',
        padding: size === 'sm' ? '7px 14px' : size === 'lg' ? '12px 24px' : '11px 20px',
        fontSize: size === 'sm' ? '12px' : '14px',
        fontWeight: 500,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        transition: 'all .12s',
        cursor: 'pointer',
        opacity: props.disabled || loading ? .6 : 1,
      }}
      disabled={loading || props.disabled}
      {...props}
    >
      {loading && <span className="spinner" style={{ width: 14, height: 14 }} />}
      {children}
    </button>
  )
}

/* ── Card ───────────────────────────────────────────── */
export function Card({ children, className = '', style = {}, ...props }) {
  return (
    <div style={{
      background: 'var(--white)',
      border: '1px solid var(--gray-100)',
      borderRadius: 'var(--radius-lg)',
      padding: '20px',
      boxShadow: 'var(--shadow)',
      ...style,
    }} {...props}>
      {children}
    </div>
  )
}

/* ── StatCard ───────────────────────────────────────── */
export function StatCard({ label, value, sub, badge, badgeColor = 'gray', progress, color }) {
  const badgeColors = {
    green:  { bg: 'var(--green-light)',  color: 'var(--green-dark)',  border: '#a8dbb8' },
    red:    { bg: 'var(--red-light)',    color: 'var(--red)',         border: 'var(--red-mid)' },
    yellow: { bg: 'var(--yellow-light)', color: 'var(--yellow-dark)', border: '#f3dfa8' },
    gray:   { bg: 'var(--gray-50)',      color: 'var(--gray-600)',    border: 'var(--gray-200)' },
  }
  const bc = badgeColors[badgeColor] || badgeColors.gray
  return (
    <Card>
      <div style={{ fontSize: 11, fontWeight: 500, color: 'var(--gray-400)', textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 8 }}>
        {label}
      </div>
      <div style={{ fontSize: 28, fontWeight: 600, color: color || 'var(--gray-900)', lineHeight: 1 }}>
        {value}
      </div>
      {sub && <div style={{ fontSize: 11, color: 'var(--gray-400)', marginTop: 4 }}>{sub}</div>}
      {progress !== undefined && (
        <div style={{ height: 5, background: 'var(--gray-100)', borderRadius: 3, marginTop: 10, overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${Math.min(100, progress)}%`, background: progress >= 80 ? 'var(--green)' : progress >= 60 ? '#f59e0b' : 'var(--red)', borderRadius: 3, transition: 'width .4s' }} />
        </div>
      )}
      {badge && (
        <span style={{ display: 'inline-block', fontSize: 10, padding: '2px 7px', borderRadius: 20, marginTop: 6, fontWeight: 500, background: bc.bg, color: bc.color, border: `1px solid ${bc.border}` }}>
          {badge}
        </span>
      )}
    </Card>
  )
}

/* ── Input ──────────────────────────────────────────── */
export function Input({ label, error, ...props }) {
  return (
    <div style={{ marginBottom: 14 }}>
      {label && <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--gray-600)', marginBottom: 5 }}>{label}</label>}
      <input
        style={{
          width: '100%', padding: '10px 14px', border: `1.5px solid ${error ? 'var(--red)' : 'var(--gray-200)'}`,
          borderRadius: 'var(--radius)', fontSize: 14, color: 'var(--gray-900)', background: 'var(--white)',
          transition: 'border-color .15s', outline: 'none',
        }}
        onFocus={e => e.target.style.borderColor = 'var(--red)'}
        onBlur={e => e.target.style.borderColor = error ? 'var(--red)' : 'var(--gray-200)'}
        {...props}
      />
      {error && <p style={{ fontSize: 11, color: 'var(--red)', marginTop: 3 }}>{error}</p>}
    </div>
  )
}

/* ── Select ─────────────────────────────────────────── */
export function Select({ label, children, error, ...props }) {
  return (
    <div style={{ marginBottom: 14 }}>
      {label && <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--gray-600)', marginBottom: 5 }}>{label}</label>}
      <select
        style={{
          width: '100%', padding: '10px 14px', border: `1.5px solid ${error ? 'var(--red)' : 'var(--gray-200)'}`,
          borderRadius: 'var(--radius)', fontSize: 14, color: 'var(--gray-900)', background: 'var(--white)', outline: 'none',
        }}
        onFocus={e => e.target.style.borderColor = 'var(--red)'}
        onBlur={e => e.target.style.borderColor = error ? 'var(--red)' : 'var(--gray-200)'}
        {...props}
      >
        {children}
      </select>
      {error && <p style={{ fontSize: 11, color: 'var(--red)', marginTop: 3 }}>{error}</p>}
    </div>
  )
}

/* ── Textarea ───────────────────────────────────────── */
export function Textarea({ label, error, ...props }) {
  return (
    <div style={{ marginBottom: 14 }}>
      {label && <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--gray-600)', marginBottom: 5 }}>{label}</label>}
      <textarea
        style={{
          width: '100%', padding: '10px 14px', border: `1.5px solid ${error ? 'var(--red)' : 'var(--gray-200)'}`,
          borderRadius: 'var(--radius)', fontSize: 14, color: 'var(--gray-900)', background: 'var(--white)',
          outline: 'none', resize: 'vertical', minHeight: 80,
        }}
        onFocus={e => e.target.style.borderColor = 'var(--red)'}
        onBlur={e => e.target.style.borderColor = error ? 'var(--red)' : 'var(--gray-200)'}
        {...props}
      />
      {error && <p style={{ fontSize: 11, color: 'var(--red)', marginTop: 3 }}>{error}</p>}
    </div>
  )
}

/* ── Modal ──────────────────────────────────────────── */
export function Modal({ open, onClose, title, children }) {
  if (!open) return null
  return (
    <div onClick={onClose} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.4)', zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}>
      <div onClick={e => e.stopPropagation()} style={{ background: 'var(--white)', borderRadius: 'var(--radius-lg)', padding: 24, width: '100%', maxWidth: 440, boxShadow: 'var(--shadow-md)', animation: 'fadeIn .15s ease' }}>
        {title && <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 16 }}>{title}</h2>}
        {children}
      </div>
    </div>
  )
}

/* ── Alert ──────────────────────────────────────────── */
export function Alert({ type = 'info', children }) {
  const styles = {
    info:    { bg: 'var(--blue-light)',   color: 'var(--blue-dark)',   border: '#bfdbfe' },
    success: { bg: 'var(--green-light)',  color: 'var(--green-dark)',  border: '#a8dbb8' },
    warning: { bg: 'var(--yellow-light)', color: 'var(--yellow-dark)', border: '#f3dfa8' },
    error:   { bg: 'var(--red-light)',    color: 'var(--red)',         border: 'var(--red-mid)' },
  }
  const s = styles[type] || styles.info
  return (
    <div style={{ background: s.bg, color: s.color, border: `1px solid ${s.border}`, borderRadius: 'var(--radius)', padding: '12px 16px', fontSize: 13, marginBottom: 16, lineHeight: 1.5 }}>
      {children}
    </div>
  )
}

/* ── Badge ──────────────────────────────────────────── */
export function Badge({ status, label }) {
  const map = {
    PRESENT:  { bg: 'var(--green-light)', color: 'var(--green-dark)', border: '#a8dbb8', label: 'Present' },
    LATE:     { bg: 'var(--yellow-light)', color: 'var(--yellow-dark)', border: '#f3dfa8', label: 'Late' },
    ABSENT:   { bg: 'var(--red-light)', color: 'var(--red)', border: 'var(--red-mid)', label: 'Absent' },
    EXCUSED:  { bg: '#f0f4ff', color: '#1d4ed8', border: '#bfdbfe', label: 'Excused' },
    ACCEPTED: { bg: 'var(--green-light)', color: 'var(--green-dark)', border: '#a8dbb8', label: 'Accepted' },
    APPROVED: { bg: 'var(--green-light)', color: 'var(--green-dark)', border: '#a8dbb8', label: 'Accepted' },
    REJECTED: { bg: 'var(--red-light)', color: 'var(--red)', border: 'var(--red-mid)', label: 'Rejected' },
    PENDING:  { bg: 'var(--yellow-light)', color: 'var(--yellow-dark)', border: '#f3dfa8', label: 'Pending' },
    ACTIVE:   { bg: 'var(--green-light)', color: 'var(--green-dark)', border: '#a8dbb8', label: 'Active' },
    INACTIVE: { bg: 'var(--gray-50)', color: 'var(--gray-600)', border: 'var(--gray-200)', label: 'Inactive' },
    MANUAL:   { bg: '#faf5ff', color: '#7c3aed', border: '#ddd6fe', label: 'Manual' },
    HOLIDAY:  { bg: '#fef3c7', color: '#92400e', border: '#fde68a', label: 'Holiday' },
  }
  const s = map[status?.toUpperCase()] || { bg: 'var(--gray-50)', color: 'var(--gray-600)', border: 'var(--gray-200)', label: status || '—' }
  return (
    <span style={{ display: 'inline-block', fontSize: 11, fontWeight: 500, padding: '3px 9px', borderRadius: 20, background: s.bg, color: s.color, border: `1px solid ${s.border}` }}>
      {label || s.label}
    </span>
  )
}

/* ── Table ──────────────────────────────────────────── */
export function Table({ columns, rows, emptyMessage = 'No records found' }) {
  return (
    <div style={{ overflowX: 'auto', borderRadius: 'var(--radius)', border: '1px solid var(--gray-100)' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            {columns.map(col => (
              <th key={col.key} style={{ fontSize: 11, fontWeight: 500, color: 'var(--gray-400)', textTransform: 'uppercase', letterSpacing: '.06em', padding: '10px 14px', textAlign: 'left', background: 'var(--gray-50)', borderBottom: '1px solid var(--gray-100)' }}>
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr><td colSpan={columns.length} style={{ padding: 40, textAlign: 'center', color: 'var(--gray-400)', fontSize: 13 }}>{emptyMessage}</td></tr>
          ) : rows.map((row, i) => (
            <tr key={i} style={{ borderBottom: i < rows.length - 1 ? '1px solid var(--gray-50)' : 'none' }}
                onMouseEnter={e => e.currentTarget.style.background = 'var(--gray-50)'}
                onMouseLeave={e => e.currentTarget.style.background = ''}>
              {columns.map(col => (
                <td key={col.key} style={{ padding: '11px 14px', fontSize: 13, color: col.strong ? 'var(--gray-900)' : 'var(--gray-600)', fontWeight: col.strong ? 500 : 400 }}>
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
    <div style={{ padding: '24px 28px 0', borderBottom: '1px solid var(--gray-100)', background: 'var(--white)', position: 'sticky', top: 44, zIndex: 10 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', paddingBottom: 16 }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 600, color: 'var(--gray-900)' }}>{title}</h1>
          {subtitle && <p style={{ fontSize: 13, color: 'var(--gray-400)', marginTop: 2 }}>{subtitle}</p>}
        </div>
        {actions && <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>{actions}</div>}
      </div>
    </div>
  )
}

/* ── Loading spinner page ───────────────────────────── */
export function LoadingPage() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '60vh', flexDirection: 'column', gap: 12 }}>
      <div className="spinner spinner-lg" />
      <p style={{ color: 'var(--gray-400)', fontSize: 13 }}>Loading...</p>
    </div>
  )
}

/* ── Empty state ────────────────────────────────────── */
export function Empty({ message = 'Nothing here yet' }) {
  return (
    <div style={{ textAlign: 'center', padding: '48px 20px', color: 'var(--gray-400)' }}>
      <div style={{ fontSize: 36, marginBottom: 12 }}>📭</div>
      <p style={{ fontSize: 13 }}>{message}</p>
    </div>
  )
}

/* ── Skeleton loading rows ──────────────────────────── */
export function Skeleton({ rows = 4, height = 18 }) {
  return (
    <div>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height, marginBottom: 10, borderRadius: 6 }} />
      ))}
    </div>
  )
}

/* ── Pagination ─────────────────────────────────────── */
export function Pagination({ page, totalPages, totalElements, size, onChange, pageSizeOptions = [10, 20, 50, 100] }) {
  if (totalPages <= 1 && totalElements <= size) return null
  const pages = Array.from({ length: Math.max(1, totalPages) }, (_, i) => i)
  const visible = pages.length <= 7 ? pages : page <= 3 ? pages.slice(0, 5).concat(-1, pages.slice(-1)) : page >= totalPages - 3 ? [pages[0], -1].concat(pages.slice(-5)) : [pages[0], -1].concat(pages.slice(page - 1, page + 2), -1, pages.slice(-1))
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', marginTop: 14 }}>
      <span style={{ fontSize: 12, color: 'var(--gray-400)' }}>
        {totalElements === 0 ? 'No results' : `Showing ${page * size + 1}–${Math.min((page + 1) * size, totalElements)} of ${totalElements}`}
      </span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        <select
          value={size}
          onChange={e => onChange(0, Number(e.target.value))}
          style={{ fontSize: 12, padding: '4px 6px', borderRadius: 6, border: '1px solid var(--gray-200)', background: 'var(--white)', color: 'var(--gray-600)' }}
        >
          {pageSizeOptions.map(s => <option key={s} value={s}>{s} / page</option>)}
        </select>
        <button onClick={() => onChange(page - 1, size)} disabled={page === 0}
          style={{ padding: '5px 10px', fontSize: 12, borderRadius: 6, border: '1px solid var(--gray-200)', background: 'var(--white)', color: page === 0 ? 'var(--gray-300)' : 'var(--gray-600)', cursor: page === 0 ? 'not-allowed' : 'pointer' }}>
          Prev
        </button>
        {visible.map((p, i) => p === -1
          ? <span key={`e${i}`} style={{ fontSize: 12, color: 'var(--gray-400)', padding: '0 4px' }}>…</span>
          : <button key={p} onClick={() => onChange(p, size)}
              style={{ padding: '5px 10px', fontSize: 12, borderRadius: 6, border: '1px solid var(--gray-200)', background: p === page ? 'var(--red)' : 'var(--white)', color: p === page ? '#fff' : 'var(--gray-600)', cursor: 'pointer', fontWeight: p === page ? 600 : 400 }}>
              {p + 1}
            </button>)}
        <button onClick={() => onChange(page + 1, size)} disabled={page >= totalPages - 1}
          style={{ padding: '5px 10px', fontSize: 12, borderRadius: 6, border: '1px solid var(--gray-200)', background: 'var(--white)', color: page >= totalPages - 1 ? 'var(--gray-300)' : 'var(--gray-600)', cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer' }}>
          Next
        </button>
      </div>
    </div>
  )
}

/* ── Rating chip (EXCELLENT/GOOD/FAIR/POOR) ─────────── */
export function RatingChip({ rating }) {
  const map = {
    EXCELLENT: { bg: 'var(--green-light)', color: 'var(--green-dark)', border: '#a8dbb8' },
    GOOD:      { bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe' },
    FAIR:      { bg: 'var(--yellow-light)', color: 'var(--yellow-dark)', border: '#f3dfa8' },
    POOR:      { bg: 'var(--red-light)', color: 'var(--red)', border: 'var(--red-mid)' },
  }
  const s = map[rating] || { bg: 'var(--gray-50)', color: 'var(--gray-600)', border: 'var(--gray-200)' }
  return (
    <span style={{ display: 'inline-block', fontSize: 10, fontWeight: 600, padding: '3px 8px', borderRadius: 20, background: s.bg, color: s.color, border: `1px solid ${s.border}`, textTransform: 'uppercase', letterSpacing: '.04em' }}>
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
