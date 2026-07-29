import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import App from './App'
import { ThemeProvider } from './context/ThemeContext'
import { SchoolProvider } from './context/SchoolContext'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ThemeProvider>
      <SchoolProvider>
        <BrowserRouter>
          <App />
          <Toaster
          position="bottom-right"
          toastOptions={{
            duration: 3500,
            style: {
              background: '#1c1917',
              color: '#fff',
              fontSize: '13px',
              fontFamily: "'DM Sans', sans-serif",
              borderRadius: '10px',
            },
            success: { iconTheme: { primary: '#22c55e', secondary: '#fff' } },
            error:   { iconTheme: { primary: '#C0392B', secondary: '#fff' } },
          }}
        />
        </BrowserRouter>
      </SchoolProvider>
    </ThemeProvider>
  </React.StrictMode>
)
