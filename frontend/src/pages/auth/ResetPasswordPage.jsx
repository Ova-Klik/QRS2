import React, { useState } from 'react'
import { useSearchParams, Link, useNavigate } from 'react-router-dom'
import { authApi } from '../../api/client'
import { useTheme } from '../../context/ThemeContext'
import { useSchool } from '../../context/SchoolContext'
import { Input, Button, Alert } from '../../components/common/UI'
import toast from 'react-hot-toast'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const navigate = useNavigate()
  const { dark, toggle } = useTheme()
  const { settings } = useSchool()

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [status, setStatus] = useState(null) // success | error
  const [message, setMessage] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!token) {
      setStatus('error')
      setMessage('Missing password reset token.')
      return
    }
    if (newPassword.length < 6) {
      setStatus('error')
      setMessage('Password must be at least 6 characters long.')
      return
    }
    if (newPassword !== confirmPassword) {
      setStatus('error')
      setMessage('Passwords do not match.')
      return
    }

    setSubmitting(true)
    setStatus(null)
    setMessage('')

    try {
      const res = await authApi.resetPassword(token, newPassword)
      setStatus('success')
      setMessage(res.data?.message || 'Your password has been reset successfully! You can now log in.')
      toast.success('Password reset successfully!')
    } catch (err) {
      setStatus('error')
      setMessage(err.response?.data?.message || 'Failed to reset password. The link may be invalid or expired.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-off flex flex-col justify-between">
      {/* Top bar */}
      <div className="flex flex-wrap items-center justify-between px-6 py-3 border-b border-gray-100 bg-white">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 bg-red rounded-md flex items-center justify-center">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="3" width="7" height="7" rx="1"/>
              <rect x="14" y="3" width="7" height="7" rx="1"/>
              <rect x="3" y="14" width="7" height="7" rx="1"/>
              <rect x="14" y="14" width="3" height="3"/>
            </svg>
          </div>
          <span className="text-[15px] font-semibold text-gray-900">QR Attendance System</span>
        </div>
        <div className="flex items-center gap-2">
          <Link to="/" className="text-xs text-gray-500 no-underline px-3 py-1.5 rounded-md border border-gray-200 hover:border-gray-400">
            Back to Login
          </Link>
          <button
            onClick={toggle}
            title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            className={
              dark
                ? 'inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-[#2e2e42] border border-gray-200 text-[#c0c0d8] text-xs font-medium cursor-pointer leading-none'
                : 'inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-gray-50 border border-gray-200 text-gray-600 text-xs font-medium cursor-pointer leading-none'
            }
          >
            {dark ? (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> Light</>
            ) : (
              <><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> Dark</>
            )}
          </button>
        </div>
      </div>

      {/* Main Form Content */}
      <div className="flex-1 flex items-center justify-center px-4 py-10 sm:px-6">
        <div className="bg-white border border-gray-100 rounded-lg p-7 shadow-md w-full max-w-[440px]">
          <div className="text-center mb-6">
            <h1 className="text-2xl font-bold text-gray-900 mb-1">Reset Password</h1>
            <p className="text-[13px] text-gray-400">
              Enter your new password below to update your account credentials.
            </p>
          </div>

          {!token && (
            <Alert type="error">
              No password reset token provided in URL. Please click the reset link sent to your email address.
            </Alert>
          )}

          {status === 'success' ? (
            <div className="text-center py-3 space-y-4">
              <Alert type="success">{message}</Alert>
              <Button onClick={() => navigate('/')} className="w-full justify-center">
                Proceed to Sign In
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {status === 'error' && <Alert type="error">{message}</Alert>}

              <Input
                label="New Password"
                type="password"
                required
                minLength={6}
                placeholder="At least 6 characters"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />

              <Input
                label="Confirm Password"
                type="password"
                required
                minLength={6}
                placeholder="Re-enter new password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />

              <Button
                type="submit"
                loading={submitting}
                disabled={!token}
                className="w-full justify-center mt-2"
              >
                Reset Password
              </Button>

              <div className="text-center pt-3">
                <Link to="/" className="text-xs text-gray-500 hover:text-gray-800 no-underline">
                  Cancel and return to login
                </Link>
              </div>
            </form>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="text-center px-6 py-4 border-t border-gray-100 text-xs text-gray-400">
        {settings?.school_name || 'Tech School'} · {settings?.school_address || 'Lagos, Nigeria'}
      </div>
    </div>
  )
}
