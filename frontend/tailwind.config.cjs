const path = require('path');

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    path.resolve(__dirname, './index.html'),
    path.resolve(__dirname, './src/**/*.{js,ts,jsx,tsx}'),
  ],
  theme: {
    extend: {
      colors: {
        darknavy: {
          50: '#f1f3f9',
          100: '#e1e7f2',
          200: '#c2cee5',
          300: '#93a9d2',
          400: '#5e7eb9',
          500: '#3c5ca0',
          600: '#2f4983',
          700: '#273c6b',
          800: '#1d2a4e',
          900: '#0f172a', // standard slate-900 / dark navy
          950: '#020617', // slate-950
        }
      },
      fontFamily: {
        display: ['Outfit', 'Inter', 'sans-serif'],
        body: ['Inter', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
