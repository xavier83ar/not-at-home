/** Loads the current user on every server-side render; the browser gets it from the payload. */
export default defineNuxtPlugin({
  name: 'auth',
  dependsOn: ['api'],
  async setup() {
    if (import.meta.server) {
      await useAuth().fetchUser()
    }
  },
})
