import type { components } from '~/types/api'

export type CurrentUser = components['schemas']['CurrentUserResponse']

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
  async function login(login: string, password: string) {
    user.value = await $api<CurrentUser>('/api/auth/login', {
      method: 'POST',
      body: { login, password },
    })
  }

  async function logout() {
    try {
      await $api('/api/auth/logout', { method: 'POST' })
    } finally {
      user.value = null
    }
  }

  return { user, isLoggedIn: computed(() => user.value !== null), fetchUser, login, logout }
}
