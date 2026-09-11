import React, { useEffect, useState } from 'react'
import { useSearchParams, useNavigate, Link } from 'react-router-dom'
import { authApi } from '../../api/client'
import { useTheme } from '../../context/ThemeContext'
import { useSchool } from '../../context/SchoolContext'
import { Input, Button, Alert } from '../../components/common/UI'

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const navigate = useNavigate()
  const { dark, toggle } = useTheme()
  const { settings } = useSchool()

  const [status, setStatus] = useState('verifying') // verifying | success | error
  const [message, setMessage] = useState('')
  const [resendEmail, setResendEmail] = useState('')
  const [resending, setResending] = useState(false)
  const [resendMessage, setResendMessage] = useState('')
  const [resendError, setResendError] = useState('')

  useEffect(() => {
    if (!token) {
      setStatus('error')
      setMessage('No verification token provided in URL.')
      return
    }

    let isMounted = true
    authApi.verifyEmail(token)
      .then(res => {
        if (!isMounted) return
        setStatus('success')
        setMessage(res.data?.message || 'Your email address has been verified successfully!')
      })
      .catch(err => {
        if (!isMounted) return
        setStatus('error')
        setMessage(err.response?.data?.message || 'Verification failed. The token may be invalid or expired.')
      })

    return () => { isMounted = false }
  }, [token])

  const handleResend = async (e) => {
    e.preventDefault()
    if (!resendEmail) return
    setResending(true)
    setResendMessage('')
    setResendError('')
    try {
      const res = await authApi.resendVerification(resendEmail)
      setResendMessage(res.data?.message || 'Verification link sent! Please check your inbox.')
    } catch (err) {
      setResendError(err.response?.data?.message || 'Failed to resend verification email.')
    } finally {
      setResending(false)
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

      {/* Main Content */}
      <div className="flex-1 flex items-center justify-center px-4 py-10 sm:px-6">
        <div className="bg-white border border-gray-100 rounded-lg p-7 shadow-md w-full max-w-[440px]">
          {status === 'verifying' && (
            <div className="text-center py-8">
              <div className="mx-auto mb-4 inline-block h-9 w-9 rounded-full border-[3px] border-gray-200 border-t-red animate-spin" />
              <h2 className="text-lg font-semibold text-gray-900 mb-1">Verifying Email</h2>
              <p className="text-[13px] text-gray-400">Validating your email verification token...</p>
            </div>
          )}

          {status === 'success' && (
            <div className="text-center py-4 space-y-4">
              <div className="mx-auto w-12 h-12 rounded-full bg-green-light text-green flex items-center justify-center text-2xl font-bold border border-[#a8dbb8]">
                ✓
              </div>
              <h2 className="text-xl font-bold text-gray-900">Email Verified!</h2>
              <Alert type="success">{message}</Alert>
              <Button onClick={() => navigate('/')} className="w-full justify-center">
                Proceed to Sign In
              </Button>
            </div>
          )}

          {status === 'error' && (
            <div>
              <div className="text-center mb-6">
                <div className="mx-auto w-12 h-12 rounded-full bg-red-light text-red flex items-center justify-center text-2xl font-bold border border-red-mid mb-3">
                  !
                </div>
                <h2 className="text-xl font-bold text-gray-900 mb-1">Verification Failed</h2>
                <Alert type="error">{message}</Alert>
              </div>

              <div className="border-t border-gray-100 pt-5 mt-5">
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">
                  Resend Verification Email
                </h3>
                {resendMessage && <Alert type="success">{resendMessage}</Alert>}
                {resendError && <Alert type="error">{resendError}</Alert>}
                <form onSubmit={handleResend} className="space-y-3">
                  <Input
                    label="Registered Email"
                    type="email"
                    required
                    placeholder="you@school.edu"
                    value={resendEmail}
                    onChange={(e) => setResendEmail(e.target.value)}
                  />
                  <Button type="submit" loading={resending} className="w-full justify-center">
                    Resend Email Link
                  </Button>
                </form>
              </div>

              <div className="text-center mt-6">
                <Link to="/" className="text-xs text-gray-500 hover:text-gray-800 no-underline">
                  Return to Home
                </Link>
              </div>
            </div>
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
