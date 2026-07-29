import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import { StudentDashboard, StudentScan, StudentHistory, StudentExcuse } from './pages/student/StudentPages'
import { FacilitatorDashboard, FacilitatorQR, FacilitatorManual, FacilitatorReports, FacilitatorExcuses } from './pages/facilitator/FacilitatorPages'
import { AdminDashboard, AdminStudents, AdminFacilitators, AdminCohorts, AdminDevices, AdminAudit, AdminAnalytics } from './pages/admin/AdminPages'
import { StudentSettings, FacilitatorSettings, AdminSettings } from './pages/settings/SettingsPages'
import { ProjectionPage } from './pages/public/ProjectionPage'

function RequireAuth({ children, roles }) {
  const { user, loading } = useAuth()
  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: 'var(--white)' }}>
      <div style={{ textAlign: 'center' }}>
        <div className="spinner spinner-lg" style={{ margin: '0 auto 12px' }} />
        <p style={{ color: 'var(--gray-400)', fontSize: 13 }}>Loading...</p>
      </div>
    </div>
  )
  if (!user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to={defaultRoute(user.role)} replace />
  return children
}

function defaultRoute(role) {
  return role === 'STUDENT' ? '/student' : role === 'FACILITATOR' ? '/facilitator' : '/admin'
}

function RoleRedirect() {
  const { user, loading } = useAuth()
  if (loading) return null
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={defaultRoute(user.role)} replace />
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Public */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/project" element={<ProjectionPage />} />
        <Route path="/" element={<RoleRedirect />} />

        {/* Student */}
        <Route path="/student" element={
          <RequireAuth roles={['STUDENT']}>
            <AppLayout><StudentDashboard /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/student/scan" element={
          <RequireAuth roles={['STUDENT']}>
            <AppLayout><StudentScan /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/student/history" element={
          <RequireAuth roles={['STUDENT']}>
            <AppLayout><StudentHistory /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/student/excuse" element={
          <RequireAuth roles={['STUDENT']}>
            <AppLayout><StudentExcuse /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/student/settings" element={
          <RequireAuth roles={['STUDENT']}>
            <AppLayout><StudentSettings /></AppLayout>
          </RequireAuth>
        } />

        {/* Facilitator */}
        <Route path="/facilitator" element={
          <RequireAuth roles={['FACILITATOR']}>
            <AppLayout><FacilitatorDashboard /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/facilitator/qr" element={
          <RequireAuth roles={['FACILITATOR']}>
            <AppLayout><FacilitatorQR /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/facilitator/manual" element={
          <RequireAuth roles={['FACILITATOR']}>
            <AppLayout><FacilitatorManual /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/facilitator/excuses" element={
          <RequireAuth roles={['FACILITATOR']}>
            <AppLayout><FacilitatorExcuses /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/facilitator/reports" element={
          <RequireAuth roles={['FACILITATOR']}>
            <AppLayout><FacilitatorReports /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/facilitator/settings" element={
          <RequireAuth roles={['FACILITATOR']}>
            <AppLayout><FacilitatorSettings /></AppLayout>
          </RequireAuth>
        } />

        {/* Admin */}
        <Route path="/admin" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminDashboard /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/admin/students" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminStudents /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/admin/facilitators" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminFacilitators /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/admin/cohorts" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminCohorts /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/admin/devices" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminDevices /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/admin/audit" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminAudit /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/admin/analytics" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminAnalytics /></AppLayout>
          </RequireAuth>
        } />
        <Route path="/admin/settings" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminSettings /></AppLayout>
          </RequireAuth>
        } />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
