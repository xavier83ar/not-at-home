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
      <BFormGroup label="Username or email" label-for="login" class="mb-3">
        <BFormInput
          id="login"
          v-model="form.login"
          autocomplete="username"
          autofocus
          required
          :state="fieldErrors.login ? false : null"
        />
        <BFormInvalidFeedback :state="fieldErrors.login ? false : null">
          {{ fieldErrors.login?.[0] }}
        </BFormInvalidFeedback>
      </BFormGroup>

      <BFormGroup label="Password" label-for="password" class="mb-4">
        <BFormInput
          id="password"
          v-model="form.password"
          type="password"
          autocomplete="current-password"
          required
          :state="fieldErrors.password ? false : null"
        />
        <BFormInvalidFeedback :state="fieldErrors.password ? false : null">
          {{ fieldErrors.password?.[0] }}
        </BFormInvalidFeedback>
      </BFormGroup>

      <BButton type="submit" variant="primary" class="w-100" :disabled="submitting || !hydrated">
        {{ submitting ? 'Logging in…' : 'Log in' }}
      </BButton>
    </BForm>
  </section>
</template>

<style scoped>
.auth-card {
  max-width: 26rem;
}
</style>
