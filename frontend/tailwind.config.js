/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef4ff', 100: '#dbe7ff', 200: '#bfd3ff', 300: '#93b5fd', 400: '#608cfa',
          500: '#3b66f5', 600: '#2549ea', 700: '#1d37d7', 800: '#1e2fae', 900: '#1e2e89', 950: '#121a4f'
        }
      },
      fontFamily: { sans: ['Inter', 'system-ui', 'Segoe UI', 'Roboto', 'sans-serif'] },
      boxShadow: {
        xs: '0 1px 2px 0 rgb(15 23 42 / 0.04)',
        card: '0 1px 2px 0 rgb(15 23 42 / 0.04), 0 1px 3px 0 rgb(15 23 42 / 0.04)',
        pop: '0 10px 38px -10px rgb(15 23 42 / 0.25), 0 10px 20px -15px rgb(15 23 42 / 0.2)'
      },
      keyframes: {
        'fade-in': { from: { opacity: '0', transform: 'translateY(4px)' }, to: { opacity: '1', transform: 'none' } }
      },
      animation: { 'fade-in': 'fade-in 150ms ease-out' }
    }
  },
  plugins: []
};
