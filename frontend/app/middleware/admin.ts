/** For pages that need an admin: `definePageMeta({ middleware: 'admin' })`. */
export default defineNuxtRouteMiddleware((to) => {
  const { user } = useAuth()
  const decision = decideAccess('admin', user.value, to.fullPath)
  if ('redirect' in decision) return navigateTo(decision.redirect)
  if ('forbidden' in decision) {
    // On the server this makes the response a real 403. `fatal` makes the browser show the error
    // page too when this happens during a client-side navigation, instead of staying put.
    return abortNavigation(
      createError({ statusCode: 403, statusMessage: 'Forbidden', fatal: true }),
    )
  }
})
