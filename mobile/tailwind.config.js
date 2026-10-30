/** @type {import('tailwindcss').Config} */
module.exports = {
  // hover: só em aparelho com mouse — no celular o :hover ficava preso depois do toque.
  future: { hoverOnlyWhenSupported: true },
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#0052A5', // Azul corporativo
        },
        accent: {
          DEFAULT: '#F59E0B', // Âmbar operacional
        },
        surface: {
          DEFAULT: '#F9FAFB', // Light mode surface
          dark: '#111827',    // Dark mode surface
        },
        success: '#10B981',
        error: '#EF4444',
        warning: '#F59E0B',
      },
      spacing: {
        '14': '56px', // Touch target mínimo de 56dp
        '16': '64px', // Bottom navigation height
      }
    },
  },
  plugins: [],
}
