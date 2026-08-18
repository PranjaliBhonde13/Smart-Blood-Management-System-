/**
 * Tailwind config — Smart Blood Emergency Network design system.
 * `darkMode: 'class'` toggles dark mode via a class on <html>.
 *
 * Design language: premium healthcare — glassmorphism, soft UI, Material 3 /
 * Apple HIG inspiration. Semantic tokens below map to the brand palette so the
 * whole app speaks one visual language.
 */
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Primary — Medical Red (#DC2626). Kept as `brand` for back-compat.
        brand: {
          50: '#fef2f2', 100: '#fee2e2', 200: '#fecaca', 300: '#fca5a5',
          400: '#f87171', 500: '#ef4444', 600: '#dc2626', 700: '#b91c1c',
          800: '#991b1b', 900: '#7f1d1d', 950: '#450a0a',
        },
        // Secondary — Blue (#2563EB)
        ocean: {
          50: '#eff6ff', 100: '#dbeafe', 200: '#bfdbfe', 300: '#93c5fd',
          400: '#60a5fa', 500: '#3b82f6', 600: '#2563eb', 700: '#1d4ed8',
          800: '#1e40af', 900: '#1e3a8a', 950: '#172554',
        },
        // Semantic aliases used across status/feedback surfaces.
        success: '#16a34a',
        emergency: '#ef4444',
        warning: '#f59e0b',
        info: '#2563eb',
      },
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['"Plus Jakarta Sans"', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      borderRadius: { xl2: '1rem', '4xl': '2rem' },
      boxShadow: {
        card: '0 4px 20px -2px rgba(15,23,42,0.08)',
        'card-hover': '0 18px 40px -12px rgba(15,23,42,0.18)',
        soft: '0 2px 8px -2px rgba(15,23,42,0.06), 0 8px 24px -8px rgba(15,23,42,0.10)',
        glow: '0 0 0 4px rgba(220,38,38,0.12)',
        'glow-blue': '0 0 0 4px rgba(37,99,235,0.12)',
        emergency: '0 10px 30px -6px rgba(220,38,38,0.45)',
      },
      backgroundImage: {
        'grad-brand': 'linear-gradient(135deg, #ef4444 0%, #dc2626 55%, #b91c1c 100%)',
        'grad-ocean': 'linear-gradient(135deg, #3b82f6 0%, #2563eb 55%, #1d4ed8 100%)',
        'grad-mesh':
          'radial-gradient(at 0% 0%, rgba(220,38,38,0.12) 0px, transparent 50%), ' +
          'radial-gradient(at 100% 0%, rgba(37,99,235,0.10) 0px, transparent 50%), ' +
          'radial-gradient(at 80% 100%, rgba(239,68,68,0.10) 0px, transparent 50%)',
      },
      keyframes: {
        'fade-in': { from: { opacity: 0 }, to: { opacity: 1 } },
        'fade-up': {
          from: { opacity: 0, transform: 'translateY(12px)' },
          to: { opacity: 1, transform: 'translateY(0)' },
        },
        'scale-in': {
          from: { opacity: 0, transform: 'scale(.96)' },
          to: { opacity: 1, transform: 'scale(1)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        'pulse-ring': {
          '0%': { boxShadow: '0 0 0 0 rgba(220,38,38,0.45)' },
          '70%': { boxShadow: '0 0 0 14px rgba(220,38,38,0)' },
          '100%': { boxShadow: '0 0 0 0 rgba(220,38,38,0)' },
        },
        float: {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        heartbeat: {
          '0%,100%': { transform: 'scale(1)' },
          '15%': { transform: 'scale(1.18)' },
          '30%': { transform: 'scale(1)' },
          '45%': { transform: 'scale(1.12)' },
          '60%': { transform: 'scale(1)' },
        },
      },
      animation: {
        'fade-in': 'fade-in .4s ease both',
        'fade-up': 'fade-up .5s cubic-bezier(.16,1,.3,1) both',
        'scale-in': 'scale-in .35s cubic-bezier(.16,1,.3,1) both',
        'pulse-ring': 'pulse-ring 2s cubic-bezier(.66,0,0,1) infinite',
        float: 'float 6s ease-in-out infinite',
        heartbeat: 'heartbeat 1.6s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
