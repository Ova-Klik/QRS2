import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || (import.meta.env.PROD ? 'https://qrs-attendance-api.onrender.com/api' : '/api'),
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Attach stored token on every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('qrs_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Auth-flow requests return 401 as a normal outcome (e.g. wrong credentials).
// Redirecting the browser for these breaks login/registration UX.
const AUTH_FLOW_URLS = ['/auth/login', '/auth/register', '/auth/webauthn']

// Handle 401 globally
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      const url = err.config?.url || ''
      const isAuthFlow = AUTH_FLOW_URLS.some(prefix => url.startsWith(prefix))
      if (!isAuthFlow) {
        localStorage.removeItem('qrs_token')
        if (window.location.pathname !== '/') {
          const qrs = new URLSearchParams(window.location.search).get('token')
          window.location.href = '/' + (qrs ? `?qrs=${encodeURIComponent(qrs)}` : '')
        }
      }
    }
    return Promise.reject(err)
  }
)

export default api

// Trigger a browser download for an export blob response.
export function downloadBlob(res, fallbackName = 'download') {
  const disposition = res.headers?.['content-disposition'] || ''
  const match = disposition.match(/filename="?([^"]+)"?/)
  const filename = match ? match[1] : fallbackName
  const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => window.URL.revokeObjectURL(url), 1000)
}

// ── Typed API calls ──────────────────────────────────────

export const authApi = {
  login:          (email, password)   => api.post('/auth/login', { email, password }),
  me:             ()                  => api.get('/auth/me'),
  changePassword: (body)              => api.post('/auth/change-password', body),
  registerStudent:    (body)          => api.post('/auth/register/student', body),
  registerFacilitator:(body)          => api.post('/auth/register/facilitator', body),
  webauthnChallenge: ()               => api.post('/auth/webauthn/challenge'),
  webauthnRegister:  (body)           => api.post('/auth/webauthn/register', body),
  webauthnVerify:    (body)           => api.post('/auth/webauthn/verify', body),
}

export const adminApi = {
  // Users
  listUsers:      (role)              => api.get('/admin/users', { params: { role } }),
  getUser:        (id)                => api.get(`/admin/users/${id}`),
  createUser:     (body)              => api.post('/admin/users', body),
  updateUser:     (id, body)          => api.put(`/admin/users/${id}`, body),
  resetPassword:  (body)              => api.post('/admin/users/reset-password', body),
  // Devices
  registerDevice: (body)              => api.post('/admin/devices/register', body),
  unlockDevice:   (studentId)         => api.post(`/admin/devices/unlock/${studentId}`),
  searchDevices:  (params)            => api.get('/admin/devices/search', { params }),
  // Cohorts
  listCohorts:    ()                  => api.get('/admin/cohorts'),
  searchCohorts:  (params)            => api.get('/admin/cohorts/search', { params }),
  getCohort:      (id)                => api.get(`/admin/cohorts/${id}`),
  createCohort:   (body)              => api.post('/admin/cohorts', body),
  updateCohort:   (id, body)          => api.put(`/admin/cohorts/${id}`, body),
  deleteCohort:   (id)                => api.delete(`/admin/cohorts/${id}`),
  toggleCohort:   (id)                => api.patch(`/admin/cohorts/${id}/toggle`),
  cohortStudents: (id)                => api.get(`/admin/cohorts/${id}/students`),
  cohortStudentsPage: (id, params)    => api.get(`/admin/cohorts/${id}/students/page`, { params }),
  // Students
  searchStudents: (params)            => api.get('/admin/students/search', { params }),
  deleteStudent:  (id)                => api.delete(`/admin/students/${id}`),
  exportStudents: (params)            => api.get('/admin/students/export', { params, responseType: 'blob' }),
  // Audit
  auditLogs:      (params)            => api.get('/admin/audit', { params }),
  purgeAuditLogs: (daysOld = 30)      => api.delete('/admin/audit/purge', { params: { daysOld } }),
  // Stats
  schoolStats:    (cohortId)          => api.get('/admin/analytics/school', { params: { cohortId } }),
  calendarMonth:  (params)            => api.get('/admin/analytics/calendar', { params }),
  studentAnalytics: (studentId)       => api.get(`/admin/analytics/students/${studentId}`),
  exportStudentAttendance: (studentId, params) => api.get(`/admin/analytics/students/${studentId}/export`, { params, responseType: 'blob' }),
  exportStudentSummary: (studentId, params)   => api.get(`/admin/analytics/students/${studentId}/summary/export`, { params, responseType: 'blob' }),
  // Attendance
  searchAttendance: (params)          => api.get('/admin/attendance/search', { params }),
  exportAttendance: (params)          => api.get('/admin/attendance/export', { params, responseType: 'blob' }),
  // Holidays
  listHolidays:   ()                  => api.get('/admin/holidays'),
  createHoliday:  (body)              => api.post('/admin/holidays', body),
  updateHoliday:  (id, body)          => api.put(`/admin/holidays/${id}`, body),
  toggleHoliday:  (id)                => api.patch(`/admin/holidays/${id}/toggle`),
  deleteHoliday:  (id)                => api.delete(`/admin/holidays/${id}`),
  // Network Settings
  getNetworkSettings:  ()             => api.get('/admin/settings/network'),
  updateNetworkSettings: (body)       => api.put('/admin/settings/network', body),
}

export const facilitatorApi = {
  generateQr:     (cohortId, durationMinutes, origin) => api.post(`/facilitator/qr/generate?origin=${encodeURIComponent(origin || '')}`, { cohortId, durationMinutes }),
  getActiveQr:    (cohortId, origin)  => api.get(`/facilitator/qr/active/${cohortId}?origin=${encodeURIComponent(origin || '')}`),
  expireQr:       (sessionId)         => api.post(`/facilitator/qr/expire/${sessionId}`),
  manualAttend:   (body)              => api.post('/facilitator/attendance/manual', body),
  manualAttendanceList: (params)      => api.get('/facilitator/attendance/manual-list', { params }),
  todaySummary:   (cohortId)          => api.get(`/facilitator/attendance/today/${cohortId}`),
  searchAttendance: (params)          => api.get('/facilitator/attendance/search', { params }),
  reports:        (params)            => api.get('/facilitator/attendance/reports', { params }),
  exportReports:  (params)            => api.get('/facilitator/attendance/reports/export', { params, responseType: 'blob' }),
  calendarMonth:  (params)            => api.get('/facilitator/attendance/calendar', { params }),
  exportAttendance: (params)          => api.get('/facilitator/attendance/export', { params, responseType: 'blob' }),
  myCohorts:      ()                  => api.get('/facilitator/cohorts'),
  dashboard:      (params)            => api.get('/facilitator/dashboard', { params }),
  cohortExcuses:  (cohortId)          => api.get(`/facilitator/excuse-requests/${cohortId}`),
  reviewExcuse:   (requestId, body)   => api.patch(`/facilitator/excuse-requests/${requestId}/review`, body),
  getNetworkSettings:  ()             => api.get('/facilitator/settings/network'),
  updateNetworkSettings: (body)       => api.put('/facilitator/settings/network', body),
}

export const studentApi = {
  scan:           (body)              => api.post('/student/attendance/scan', body),
  history:        ()                  => api.get('/student/attendance/history'),
  historyPage:    (params)            => api.get('/student/attendance/history/page', { params }),
  analytics:      ()                  => api.get('/student/attendance/analytics'),
  myCalendar:     (params)            => api.get('/student/attendance/calendar', { params }),
  exportMyAttendance: (params)        => api.get('/student/attendance/export', { params, responseType: 'blob' }),
  dashboard:      ()                  => api.get('/student/dashboard'),
  registerDevice: (body)              => api.post('/student/device/register', body),
  submitExcuse:   (body)              => api.post('/student/excuse-request', body),
  myExcuses:      ()                  => api.get('/student/excuse-request'),
}

export const publicApi = {
  listCohorts:            ()                  => api.get('/public/cohorts'),
  getQrSession:           (cohortId, origin)  => api.get(`/public/qr-session/${cohortId}?origin=${encodeURIComponent(origin || '')}`),
  getTodaySummary:        (cohortId)          => api.get(`/public/today-summary/${cohortId}`),
  getSettings:            ()                  => api.get('/public/settings'),
  exportProjectionReport: (cohortId, date)    => api.get(`/public/projection/export/${cohortId}`, { params: { date, format: 'xlsx' }, responseType: 'blob' }),
}
