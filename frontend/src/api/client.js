import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Attach stored token on every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('qrs_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Handle 401 globally
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('qrs_token')
      window.location.href = '/login'
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
  const url = window.URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
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
  // Cohorts
  listCohorts:    ()                  => api.get('/admin/cohorts'),
  createCohort:   (body)              => api.post('/admin/cohorts', body),
  toggleCohort:   (id)                => api.patch(`/admin/cohorts/${id}/toggle`),
  cohortStudents: (id)                => api.get(`/admin/cohorts/${id}/students`),
  cohortStudentsPage: (id, params)    => api.get(`/admin/cohorts/${id}/students/page`, { params }),
  exportCohort:   (id, format)        => api.get(`/admin/cohorts/${id}/export?format=${format}`, { responseType: 'blob' }),
  // Students
  searchStudents: (params)            => api.get('/admin/students/search', { params }),
  // Audit
  auditLogs:      ()                  => api.get('/admin/audit'),
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
  todaySummary:   (cohortId)          => api.get(`/facilitator/attendance/today/${cohortId}`),
  searchAttendance: (params)          => api.get('/facilitator/attendance/search', { params }),
  calendarMonth:  (params)            => api.get('/facilitator/attendance/calendar', { params }),
  exportAttendance: (params)          => api.get('/facilitator/attendance/export', { params, responseType: 'blob' }),
  myCohorts:      ()                  => api.get('/facilitator/cohorts'),
  dashboard:      ()                  => api.get('/facilitator/dashboard'),
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
  listCohorts:    ()                  => api.get('/public/cohorts'),
  getQrSession:   (cohortId, origin)  => api.get(`/public/qr-session/${cohortId}?origin=${encodeURIComponent(origin || '')}`),
  getTodaySummary:(cohortId)          => api.get(`/public/today-summary/${cohortId}`),
  getSettings:    ()                  => api.get('/public/settings'),
}
