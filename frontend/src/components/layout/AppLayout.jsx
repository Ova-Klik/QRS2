import React, { useState, useEffect } from 'react'
import { NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useTheme } from '../../context/ThemeContext'
import { useSchool } from '../../context/SchoolContext'

const ICONS = {
  grid:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><polyline points="3 9 3 3 9 3"/><polyline points="21 9 21 3 15 3"/><polyline points="3 15 3 21 9 21"/><polyline points="21 15 21 21 15 21"/></svg>,
  qr:         <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/><rect x="18" y="18" width="3" height="3"/><rect x="14" y="18" width="3" height="3"/><rect x="18" y="14" width="3" height="3"/></svg>,
  list:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>,
  edit:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4z"/></svg>,
  chart:      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>,
  calendar:   <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>,
  users:      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>,
  layers:     <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>,
  phone:      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>,
  file:       <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>,
  settings:   <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>,
  hamburger:  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>,
  close:      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>,
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
    { path: '/admin/calendar',  label: 'Calendar',   icon: 'calendar' },
    { path: '/admin/audit',      label: 'Audit Logs', icon: 'file' },
    { path: '/admin/analytics',  label: 'Analytics',  icon: 'chart' },
    { path: '/admin/settings',   label: 'Settings',   icon: 'settings' },
  ],
}

export default function AppLayout({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { dark, toggle } = useTheme()
  const { settings } = useSchool()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)
  const navItems = NAV[user?.role] || []
  const initials = user?.name?.split(' ').map(w => w[0]).slice(0, 2).join('') || '?'
  const roleLabel = { STUDENT: 'Student', FACILITATOR: 'Facilitator', SUPER_ADMIN: 'Super Admin' }[user?.role] || user?.role

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  useEffect(() => { setMobileOpen(false) }, [location.pathname])

  const handleLogout = () => { logout(); navigate('/login') }

  const sidebarWidth = isMobile ? (mobileOpen ? 220 : 0) : (collapsed ? 64 : 220)

  return (
    <div className="flex min-h-screen">
      {/* Mobile overlay */}
      {isMobile && mobileOpen && (
        <div onClick={() => setMobileOpen(false)} className="fixed inset-0 bg-black/40 z-[99]" />
      )}

      {/* Sidebar */}
      <div
        className="shrink-0 bg-white border-r border-gray-100 flex flex-col h-screen overflow-hidden z-[100]"
        style={{
          width: sidebarWidth,
          position: isMobile ? 'fixed' : 'sticky',
          top: 0,
          transition: 'width .2s, left .2s',
          left: isMobile ? (mobileOpen ? 0 : -220) : 'auto',
        }}
      >
        <div className="flex items-center gap-2.5 px-4 pt-5 pb-4 min-w-[220px]">
          <div
            onClick={() => isMobile ? setMobileOpen(false) : setCollapsed(!collapsed)}
            className="w-8 h-8 bg-red rounded-md flex items-center justify-center shrink-0 cursor-pointer"
          >
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="3" height="3"/></svg>
          </div>
          {(isMobile ? mobileOpen : !collapsed) && (
            <div className="text-[13px] font-semibold leading-tight whitespace-nowrap">
              QR Attendance<br />
              <span className="font-normal text-gray-400 text-[11px]">{settings?.school_name || 'Tech School'}</span>
            </div>
          )}
        </div>
        <div className="h-px bg-gray-100 mx-4 mb-3" />

        <nav className="flex-1 overflow-y-auto">
          {navItems.map(item => (
            <NavLink key={item.path} to={item.path} end={item.path.split('/').length <= 2}
              className={({ isActive }) =>
                `flex items-center gap-2.5 px-4 py-2 rounded-lg mx-2 cursor-pointer text-[13px] whitespace-nowrap overflow-hidden transition-colors ${
                  isActive ? 'text-red bg-red-light font-medium' : 'text-gray-600 hover:bg-gray-50'
                }`
              }>
              {ICONS[item.icon]}
              {(isMobile ? true : !collapsed) && item.label}
            </NavLink>
          ))}
        </nav>

        <div className="px-4 py-3 border-t border-gray-100">
          <div className="flex items-center gap-2.5 mb-2">
            <div className="w-8 h-8 rounded-full bg-red flex items-center justify-center text-white text-xs font-semibold shrink-0">{initials}</div>
            {(isMobile ? true : !collapsed) && (
              <div className="overflow-hidden">
                <div className="text-[13px] font-medium whitespace-nowrap overflow-hidden text-ellipsis">{user?.name}</div>
                <div className="text-[11px] text-gray-400">{roleLabel}</div>
              </div>
            )}
          </div>
          <button onClick={handleLogout}
            className="w-full py-1.5 bg-gray-50 border border-gray-100 rounded-md text-xs text-gray-600 hover:bg-gray-100 transition-colors whitespace-nowrap overflow-hidden">
            Sign out
          </button>
        </div>
      </div>

      {/* Main */}
      <div className="flex-1 flex flex-col min-h-screen overflow-x-hidden">
        {/* Topbar */}
        <div className={`h-11 bg-white border-b border-gray-100 flex items-center justify-between sticky top-0 z-20 shrink-0 ${isMobile ? 'px-3' : 'px-7'}`}>
          <div className="flex items-center gap-2">
            {isMobile && (
              <button onClick={() => setMobileOpen(true)} className="bg-transparent border-0 p-1 cursor-pointer text-gray-600">
                {ICONS.hamburger}
              </button>
            )}
            <div className="w-2 h-2 rounded-full bg-green animate-pulse" />
            <span className="text-xs text-gray-400 hidden md:inline">School Network</span>
          </div>
          <div className="flex items-center gap-2">
            <Clock />
            <span className="text-xs text-gray-400 font-medium hidden md:inline">{roleLabel}</span>
            <button
              onClick={toggle}
              title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
              className="inline-flex items-center gap-1 rounded-full text-xs font-medium cursor-pointer leading-none select-none border"
              style={{
                padding: isMobile ? '5px 8px' : '5px 10px',
                background: dark ? '#2e2e42' : 'var(--gray-50)',
                borderColor: 'var(--gray-200)',
                color: dark ? '#c0c0d8' : 'var(--gray-600)',
              }}
            >
              {dark ? (
                <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> <span className="hidden md:inline">Light</span></>
              ) : (
                <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> <span className="hidden md:inline">Dark</span></>
              )}
            </button>
          </div>
        </div>

        <div className="flex-1 animate-fade-in">{children}</div>
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
  return <span className="font-mono text-xs text-gray-400">{time}</span>
}
