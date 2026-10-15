// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  modules: ['@nuxt/eslint', '@nuxt/fonts', '@nuxtjs/color-mode', '@bootstrap-vue-next/nuxt'],

  css: ['~/assets/scss/main.scss'],

  // Follow the system setting by default; the toggle in the top bar overrides it.
  colorMode: { preference: 'system', fallback: 'light', classSuffix: '' },

  fonts: {
    families: [
      { name: 'Barlow', provider: 'google', weights: [400, 500, 600] },
      { name: 'Montserrat', provider: 'google', weights: [700, 800] },
    ],
  },

  runtimeConfig: {
    // Server only. Override at runtime with NUXT_BACKEND_URL.
    backendUrl: 'http://localhost:8080',
  },

  app: {
    head: {
      htmlAttrs: { lang: 'en' },
      meta: [
        { name: 'description', content: 'Not at Home: stories from the trail.' },
        { name: 'theme-color', content: '#1F4D3A' },
      ],
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        { rel: 'icon', type: 'image/png', sizes: '32x32', href: '/favicon-32.png' },
        { rel: 'apple-touch-icon', href: '/apple-touch-icon.png' },
        { rel: 'manifest', href: '/manifest.webmanifest' },
      ],
    },
  },

  vite: {
    css: {
      preprocessorOptions: {
        scss: {
          // Bootstrap 5.3 still uses @import and a few deprecated Sass features.
          quietDeps: true,
          silenceDeprecations: ['import', 'global-builtin', 'color-functions', 'if-function'],
        },
      },
    },
  },
})
