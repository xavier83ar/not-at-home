/** For pages that need a login: `definePageMeta({ middleware: 'auth' })`. */
export default defineNuxtRouteMiddleware((to) => {
  const { user } = useAuth()
  const decision = decideAccess('auth', user.value, to.fullPath)
  if ('redirect' in decision) return navigateTo(decision.redirect)
})
