<script setup lang="ts">
useSeoMeta({ title: 'Log in', robots: 'noindex' })

const route = useRoute()
const { isLoggedIn, login } = useAuth()

const destination = computed(() => safeRedirect(route.query.redirect))

// Nothing to do here for someone who is already logged in.
if (isLoggedIn.value) {
  await navigateTo(destination.value)
}

// Until the page is hydrated the form has no submit handler. The button stays disabled so a
// password manager's autofill + Enter can't submit it natively.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const form = reactive({ login: '', password: '' })
const submitting = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  submitting.value = true
  message.value = undefined
  fieldErrors.value = {}
  try {
    await login(form.login, form.password)
    await navigateTo(destination.value)
  } catch (error) {
    const problem = parseProblem(error)
    fieldErrors.value = problem.fieldErrors
    message.value = loginErrorMessage(problem)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-card mx-auto py-4">
    <h1 class="h3 mb-4">Log in</h1>

    <BAlert :model-value="!!message" variant="danger" data-testid="login-error">
      {{ message }}
    </BAlert>

    <BForm method="post" novalidate @submit.prevent="onSubmit">
      <FormTextField
        id="login"
        v-model="form.login"
        label="Username or email"
        autocomplete="username"
        autofocus
        :errors="fieldErrors.login"
      />
      <FormTextField
        id="password"
        v-model="form.password"
        label="Password"
        type="password"
        autocomplete="current-password"
        :errors="fieldErrors.password"
      />

      <BButton
        type="submit"
        variant="primary"
        class="w-100 mt-2"
        :disabled="submitting || !hydrated"
      >
        {{ submitting ? 'Logging in…' : 'Log in' }}
      </BButton>
    </BForm>

    <p class="mt-4 mb-0 text-center">
      New here? <NuxtLink to="/register">Create an account</NuxtLink>
    </p>
  </section>
</template>

<style scoped>
.auth-card {
  max-width: 26rem;
}
</style>
