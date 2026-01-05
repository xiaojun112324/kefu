/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#ecfeff',
          100: '#cffafe',
          200: '#a5f3fc',
          300: '#67e8f9',
          400: '#22d3ee',
          500: '#06b6d4',
          600: '#0891b2',
          700: '#0e7490',
          800: '#155e75',
          900: '#164e63'
        }
      },
      boxShadow: {
        glow: '0 0 0 2px rgba(34,211,238,.25), 0 0 30px rgba(34,211,238,.18)',
        neon: '0 10px 30px rgba(34,211,238,.35)'
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: 0, transform: 'translateY(16px) scale(.98)' },
          '100%': { opacity: 1, transform: 'translateY(0) scale(1)' }
        },
        'aurora': {
          '0%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
          '100%': { backgroundPosition: '0% 50%' }
        },
        'float': {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' }
        }
      },
      animation: {
        'fade-up': 'fade-up .45s ease forwards',
        'aurora': 'aurora 12s ease infinite',
        'float': 'float 3s ease-in-out infinite'
      }
    }
  },
  plugins: []
}
