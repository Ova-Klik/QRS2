/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        red: {
          DEFAULT: 'var(--red)',
          dark: 'var(--red-dark)',
          light: 'var(--red-light)',
          mid: 'var(--red-mid)',
        },
        white: 'var(--white)',
        off: 'var(--off)',
        gray: {
          50: 'var(--gray-50)',
          100: 'var(--gray-100)',
          200: 'var(--gray-200)',
          300: 'var(--gray-300)',
          400: 'var(--gray-400)',
          500: 'var(--gray-500)',
          600: 'var(--gray-600)',
          700: 'var(--gray-700)',
          800: '#292524',
          900: 'var(--gray-900)',
        },
        green: {
          DEFAULT: 'var(--green)',
          dark: 'var(--green-dark)',
          light: 'var(--green-light)',
        },
        yellow: {
          light: 'var(--yellow-light)',
          dark: 'var(--yellow-dark)',
        },
        blue: {
          light: 'var(--blue-light)',
          dark: 'var(--blue-dark)',
        },
      },
      fontFamily: {
        sans: ['DM Sans', 'sans-serif'],
        mono: ['DM Mono', 'monospace'],
      },
      borderRadius: {
        DEFAULT: 'var(--radius)',
        lg: 'var(--radius-lg)',
      },
      boxShadow: {
        DEFAULT: 'var(--shadow)',
        md: 'var(--shadow-md)',
      },
      keyframes: {
        'fade-in': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'sign-pulse': {
          '0%, 100%': { boxShadow: '0 0 0 0 rgba(192, 57, 43, 0.35)' },
          '50%': { boxShadow: '0 0 0 8px rgba(192, 57, 43, 0)' },
        },
      },
      animation: {
        'fade-in': 'fade-in .2s ease',
        'sign-pulse': 'sign-pulse 2.2s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
