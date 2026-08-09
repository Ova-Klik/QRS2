import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import AppLayout from './components/layout/AppLayout'
import RegisterPage from './pages/auth/RegisterPage'
import RegisterFacilitatorPage from './pages/auth/RegisterFacilitatorPage'
import VerifyEmailPage from './pages/auth/VerifyEmailPage'
import ResetPasswordPage from './pages/auth/ResetPasswordPage'
import { StudentDashboard, StudentScan, StudentHistory, StudentExcuse } from './pages/student/StudentPages'
import { FacilitatorDashboard, FacilitatorQR, FacilitatorManual, FacilitatorReports, FacilitatorExcuses } from './pages/facilitator/FacilitatorPages'
import { AdminDashboard, AdminStudents, AdminFacilitators, AdminCohorts, AdminDevices, AdminAudit, AdminAnalytics } from './pages/admin/AdminPages'
import { AdminCalendar } from './pages/admin/AdminCalendar'
import { StudentSettings, FacilitatorSettings, AdminSettings } from './pages/settings/SettingsPages'
import { ProjectionPage } from './pages/public/ProjectionPage'
import LandingPage from './pages/public/LandingPage'
import TesterPage from './pages/public/TesterPage'

function RequireAuth({ children, roles }) {
  const { user, loading } = useAuth()
  if (loading) return (
    <div className="flex items-center justify-center min-h-screen bg-white">
      <div className="text-center">
        <div className="mx-auto mb-3 inline-block h-9 w-9 rounded-full border-[3px] border-gray-200 border-t-red animate-spin" />
        <p className="text-gray-400 text-[13px]">Loading...</p>
      </div>
    </div>
  )
  if (!user) {
    const token = new URLSearchParams(window.location.search).get('token')
    const dest = '/' + (token ? `?qrs=${encodeURIComponent(token)}` : '')
    return <Navigate to={dest} replace />
  }
  if (roles && !roles.includes(user.role)) return <Navigate to={defaultRoute(user.role)} replace />
  return children
}

function defaultRoute(role) {
  return role === 'STUDENT' ? '/student' : role === 'FACILITATOR' ? '/facilitator' : '/admin'
}

function RoleRedirect() {
  const { user, loading } = useAuth()
  if (loading) return null
  if (!user) return <Navigate to="/" replace />
  return <Navigate to={defaultRoute(user.role)} replace />
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Public */}
        <Route path="/login" element={<Navigate to="/" replace />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/register/student" element={<RegisterPage />} />
        <Route path="/register/facilitator" element={<RegisterFacilitatorPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/project" element={<ProjectionPage />} />
        <Route path="/tester" element={<TesterPage />} />
        <Route path="/" element={<LandingPage />} />

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
        <Route path="/admin/calendar" element={
          <RequireAuth roles={['SUPER_ADMIN']}>
            <AppLayout><AdminCalendar /></AppLayout>
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
