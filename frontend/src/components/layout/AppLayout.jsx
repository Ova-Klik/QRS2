import React, { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useTheme } from '../../context/ThemeContext'
import { useSchool } from '../../context/SchoolContext'

const ICONS = {
  grid:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><polyline points="3 9 3 3 9 3"/><polyline points="21 9 21 3 15 3"/><polyline points="3 15 3 21 9 21"/><polyline points="21 15 21 21 15 21"/></svg>,
  qr:         <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/><rect x="18" y="18" width="3" height="3"/><rect x="14" y="18" width="3" height="3"/><rect x="18" y="14" width="3" height="3"/></svg>,
  list:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>,
  edit:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4z"/></svg>,
  chart:      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>,
  users:      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>,
  layers:     <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>,
  phone:      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>,
  file:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>,
  settings:   <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>,
}

const NAV = {
  STUDENT: [
    { path: '/student',         label: 'Dashboard',    icon: 'grid' },
    { path: '/student/scan',    label: 'Scan QR Code', icon: 'qr' },
    { path: '/student/history', label: 'My Attendance', icon: 'list' },
    { path: '/student/excuse',  label: 'Excuse Requests', icon: 'file' },
    { path: '/student/settings',label: 'Settings',     icon: 'settings' },
  ],
  FACILITATOR: [
    { path: '/facilitator',          label: 'Dashboard',         icon: 'grid' },
    { path: '/facilitator/qr',       label: 'QR Generator',      icon: 'qr' },
    { path: '/facilitator/manual',   label: 'Manual Attendance', icon: 'edit' },
    { path: '/facilitator/excuses',  label: 'Excuse Requests',   icon: 'file' },
    { path: '/facilitator/reports',  label: 'Reports',           icon: 'chart' },
    { path: '/facilitator/settings', label: 'Settings',          icon: 'settings' },
  ],
  SUPER_ADMIN: [
    { path: '/admin',            label: 'Dashboard',  icon: 'grid' },
    { path: '/admin/students',   label: 'Students',   icon: 'users' },
    { path: '/admin/facilitators', label: 'Facilitators', icon: 'users' },
    { path: '/admin/cohorts',    label: 'Cohorts',    icon: 'layers' },
    { path: '/admin/devices',    label: 'Devices',    icon: 'phone' },
    { path: '/admin/audit',      label: 'Audit Logs', icon: 'file' },
    { path: '/admin/analytics',  label: 'Analytics',  icon: 'chart' },
    { path: '/admin/settings',   label: 'Settings',   icon: 'settings' },
  ],
}

export default function AppLayout({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { dark, toggle } = useTheme()
  const { settings } = useSchool()
  const [collapsed, setCollapsed] = useState(false)
  const navItems = NAV[user?.role] || []
  const initials = user?.name?.split(' ').map(w => w[0]).slice(0, 2).join('') || '?'
  const roleLabel = { STUDENT: 'Student', FACILITATOR: 'Facilitator', SUPER_ADMIN: 'Super Admin' }[user?.role] || user?.role

  const handleLogout = () => { logout(); navigate('/login') }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* Sidebar */}
      <div style={{ width: collapsed ? 64 : 220, flexShrink: 0, background: 'var(--white)', borderRight: '1px solid var(--gray-100)', display: 'flex', flexDirection: 'column', height: '100vh', position: 'sticky', top: 0, transition: 'width .2s', overflow: 'hidden' }}>
        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '20px 16px 16px', minWidth: 220 }}>
          <div style={{ width: 32, height: 32, background: 'var(--red)', borderRadius: 7, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, cursor: 'pointer' }} onClick={() => setCollapsed(!collapsed)}>
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/></svg>
          </div>
          {!collapsed && <div style={{ fontSize: 13, fontWeight: 600, lineHeight: 1.2, whiteSpace: 'nowrap' }}>QR Attendance<br /><span style={{ fontWeight: 400, color: 'var(--gray-400)', fontSize: 11 }}>{settings?.school_name || 'Tech School'}</span></div>}
        </div>
        <div style={{ height: 1, background: 'var(--gray-100)', margin: '0 16px 12px' }} />

        {/* Nav */}
        <nav style={{ flex: 1, overflowY: 'auto' }}>
          {navItems.map(item => (
            <NavLink key={item.path} to={item.path} end={item.path.split('/').length <= 2}
              style={({ isActive }) => ({
                display: 'flex', alignItems: 'center', gap: 10, padding: '9px 16px',
                borderRadius: 8, margin: '1px 8px', cursor: 'pointer', fontSize: 13,
                textDecoration: 'none', whiteSpace: 'nowrap', overflow: 'hidden',
                color: isActive ? 'var(--red)' : 'var(--gray-600)',
                background: isActive ? 'var(--red-light)' : 'transparent',
                fontWeight: isActive ? 500 : 400,
                transition: 'all .12s',
              })}>
              {ICONS[item.icon]}
              {!collapsed && item.label}
            </NavLink>
          ))}
        </nav>

        {/* User footer */}
        <div style={{ padding: '12px 16px', borderTop: '1px solid var(--gray-100)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--red)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 12, fontWeight: 600, flexShrink: 0 }}>{initials}</div>
            {!collapsed && <div style={{ overflow: 'hidden' }}>
              <div style={{ fontSize: 13, fontWeight: 500, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{user?.name}</div>
              <div style={{ fontSize: 11, color: 'var(--gray-400)' }}>{roleLabel}</div>
            </div>}
          </div>
          <button onClick={handleLogout} style={{ width: '100%', padding: '7px', background: 'var(--gray-50)', border: '1px solid var(--gray-100)', borderRadius: 7, fontSize: 12, color: 'var(--gray-600)', cursor: 'pointer', transition: 'all .12s', whiteSpace: 'nowrap', overflow: 'hidden' }}
            onMouseEnter={e => { e.target.style.background = 'var(--red-light)'; e.target.style.color = 'var(--red)' }}
            onMouseLeave={e => { e.target.style.background = 'var(--gray-50)'; e.target.style.color = 'var(--gray-600)' }}>
            {collapsed ? '→' : 'Sign out'}
          </button>
        </div>
      </div>

      {/* Main */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: '100vh', overflow: 'auto' }}>
        {/* Topbar */}
        <div style={{ height: 44, background: 'var(--white)', borderBottom: '1px solid var(--gray-100)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px', position: 'sticky', top: 0, zIndex: 20, flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--green)', animation: 'pulse 2s infinite' }} />
            <span style={{ fontSize: 12, color: 'var(--gray-400)' }}>School Network</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Clock />
            <span style={{ fontSize: 12, color: 'var(--gray-400)', fontWeight: 500 }}>{roleLabel}</span>
            {/* Dark / Light toggle */}
            <button
              onClick={toggle}
              title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 5,
                padding: '5px 10px', borderRadius: 20,
                background: dark ? '#2e2e42' : 'var(--gray-50)',
                border: '1px solid var(--gray-200)',
                color: dark ? '#c0c0d8' : 'var(--gray-600)',
                fontSize: 12, fontWeight: 500, cursor: 'pointer',
                lineHeight: 1, userSelect: 'none',
              }}
            >
              {dark ? (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
            ) : (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
            )}
            </button>
          </div>
        </div>

        {/* Content */}
        <div style={{ flex: 1 }} className="fade-in">{children}</div>
      </div>
    </div>
  )
}

function Clock() {
  const [time, setTime] = useState(new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' }))
  React.useEffect(() => {
    const t = setInterval(() => setTime(new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' })), 1000)
    return () => clearInterval(t)
  }, [])
  return <span style={{ fontFamily: 'var(--mono)', fontSize: 12, color: 'var(--gray-400)' }}>{time}</span>
}
