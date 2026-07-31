import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { publicApi } from '../api/client'

const SchoolContext = createContext()

export function SchoolProvider({ children }) {
  const [settings, setSettings] = useState({
    school_name: 'My School',
    school_address: 'Lagos, Nigeria',
    school_email: 'admin@techschool.edu.ng',
    school_website: 'https://techschool.edu.ng',
    qr_window_start: '07:00',
    qr_window_end: '12:00',
    late_threshold: '08:31',
  })
  const [loading, setLoading] = useState(true)

  const reloadSettings = useCallback(() => {
    publicApi.getSettings()
      .then(res => {
        if (res.data) {
          setSettings(prev => ({ ...prev, ...res.data }))
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    reloadSettings()
  }, [reloadSettings])

  const updateSchoolSettings = (newSettings) => {
    setSettings(prev => ({ ...prev, ...newSettings }))
  }

  return (
    <SchoolContext.Provider value={{ settings, reloadSettings, updateSchoolSettings, loading }}>
      {children}
    </SchoolContext.Provider>
  )
}

export function useSchool() {
  return useContext(SchoolContext) || {
    settings: {
      school_name: 'My School',
      school_address: 'Lagos, Nigeria',
      school_email: 'admin@techschool.edu.ng',
      school_website: 'https://techschool.edu.ng',
      qr_window_start: '07:00',
      qr_window_end: '12:00',
      late_threshold: '08:31',
    },
    reloadSettings: () => {},
    updateSchoolSettings: () => {},
    loading: false,
  }
}
