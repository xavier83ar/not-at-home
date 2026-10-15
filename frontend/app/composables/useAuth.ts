import type { components } from '~/types/api'

export type CurrentUser = components['schemas']['CurrentUserResponse']
export type RegisterInput = components['schemas']['RegisterRequest']

/**
 * The logged-in user. The state is filled during SSR by `plugins/auth.ts` and travels to the
 * browser with the page, so there is no flash of "Log in" for people who are already logged in.
 */
export function useAuth() {
  const { $api } = useNuxtApp()
  const user = useState<CurrentUser | null>('auth-user', () => null)

  /** Asks the backend who is logged in. A 401 just means nobody is. */
  async function fetchUser() {
    try {
      user.value = await $api<CurrentUser>('/api/me')
    } catch (error) {
      user.value = null
      if (parseProblem(error).status !== 401) {
        // Backend unreachable or broken: render the page as a guest rather than failing every page.
        console.error('Could not load the current user', error)
      }
    }
  }

  /** Throws the `$api` error on failure; read it with `parseProblem`. */
  async function login(login: string, password: string, rememberMe = false) {
    user.value = await $api<CurrentUser>('/api/auth/login', {
      method: 'POST',
      body: { login, password, rememberMe },
    })
  }

  /** Creates the account and logs it in. Throws the `$api` error on failure (field errors: 400). */
  async function register(input: RegisterInput) {
    user.value = await $api<CurrentUser>('/api/auth/register', { method: 'POST', body: input })
  }

  /** Confirms an address with the token from the email link. Works logged in or not. */
  async function verifyEmail(token: string) {
    await $api('/api/auth/verify-email', { method: 'POST', body: { token } })
    // If this browser is logged in, it should see the account as verified right away.
    if (user.value) await fetchUser()
  }

  /**
   * Asks for a reset email. Resolves the same way whether or not the address has an account, so
   * pages must not say which. Throws on a malformed address (400) or a server problem.
   */
  async function requestPasswordReset(email: string) {
    await $api('/api/auth/forgot-password', { method: 'POST', body: { email } })
  }

  /** Sets a new password with the token from the email. Throws the `$api` error on failure. */
  async function resetPassword(token: string, password: string) {
    await $api('/api/auth/reset-password', { method: 'POST', body: { token, password } })
    // The backend ended every session of this user, this browser's included.
    user.value = null
  }

  async function resendVerification() {
    await $api('/api/auth/resend-verification', { method: 'POST' })
  }

  async function logout() {
    try {
      await $api('/api/auth/logout', { method: 'POST' })
    } finally {
      user.value = null
    }
  }

  return {
    user,
    isLoggedIn: computed(() => user.value !== null),
    fetchUser,
    login,
    register,
    verifyEmail,
    requestPasswordReset,
    resetPassword,
    resendVerification,
    logout,
  }
}
