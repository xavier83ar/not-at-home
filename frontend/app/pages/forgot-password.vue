<script setup lang="ts">
useSeoMeta({ title: 'Forgot your password?', robots: 'noindex' })

const { isLoggedIn, requestPasswordReset } = useAuth()

if (isLoggedIn.value) {
  await navigateTo('/')
}

// See login.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const form = reactive({ email: '' })
const submitting = ref(false)
const sent = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  submitting.value = true
  message.value = undefined
  fieldErrors.value = {}
  try {
    await requestPasswordReset(form.email)
    sent.value = true
  } catch (error) {
    const problem = parseProblem(error)
    fieldErrors.value = problem.fieldErrors
    message.value =
      problem.status === 400
        ? undefined
        : problem.status === 429
          ? rateLimitMessage(problem.retryAfterSeconds)
          : 'Something went wrong. Please try again.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-card mx-auto py-4">
    <h1 class="h3 mb-4">Forgot your password?</h1>

    <template v-if="sent">
      <!-- Worded so it is true whether or not the address has an account. -->
      <BAlert :model-value="true" variant="success" data-testid="reset-requested">
        If an account uses <strong>{{ form.email }}</strong
        >, we've sent it a link to choose a new password. The link works for 1 hour.
      </BAlert>
      <p class="mb-0"><NuxtLink to="/login">Back to log in</NuxtLink></p>
    </template>

    <template v-else>
      <p>
        Enter the email address of your account and we'll send you a link to choose a new password.
      </p>

      <BAlert :model-value="!!message" variant="danger" data-testid="forgot-error">
        {{ message }}
      </BAlert>

      <BForm method="post" novalidate @submit.prevent="onSubmit">
        <FormTextField
          id="email"
          v-model="form.email"
          label="Email"
          type="email"
          autocomplete="email"
          autofocus
          :errors="fieldErrors.email"
        />
        <BButton
          type="submit"
          variant="primary"
          class="w-100 mt-2"
          :disabled="submitting || !hydrated"
        >
          {{ submitting ? 'Sending…' : 'Send me a link' }}
        </BButton>
      </BForm>

      <p class="mt-4 mb-0 text-center"><NuxtLink to="/login">Back to log in</NuxtLink></p>
    </template>
  </section>
</template>

<style scoped>
.auth-card {
  max-width: 26rem;
}
</style>
