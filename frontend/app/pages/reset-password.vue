<script setup lang="ts">
useSeoMeta({ title: 'Choose a new password', robots: 'noindex' })
// The token is in the URL: don't pass it on to anything the page links to or loads.
useHead({ meta: [{ name: 'referrer', content: 'no-referrer' }] })

const route = useRoute()
const { resetPassword } = useAuth()

const token = computed(() => firstQueryValue(route.query.token))

// See login.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const form = reactive({ password: '' })
const submitting = ref(false)
const done = ref(false)
const message = ref<string | undefined>(
  token.value ? undefined : 'This link is missing its reset code. Open it again from the email.',
)
const requestNewLink = ref(!token.value)
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  if (!token.value) return
  submitting.value = true
  message.value = undefined
  fieldErrors.value = {}
  try {
    await resetPassword(token.value, form.password)
    done.value = true
  } catch (error) {
    const problem = parseProblem(error)
    fieldErrors.value = problem.fieldErrors
    const result = resetPasswordError(problem)
    message.value = result.message
    requestNewLink.value = result.requestNewLink
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-card mx-auto py-4">
    <h1 class="h3 mb-4">Choose a new password</h1>

    <template v-if="done">
      <BAlert :model-value="true" variant="success" data-testid="password-changed">
        Your password has been changed. For your safety, you've been logged out on every device.
      </BAlert>
      <NuxtLink to="/login" class="btn btn-primary w-100">Log in</NuxtLink>
    </template>

    <template v-else>
      <BAlert :model-value="!!message" variant="danger" data-testid="reset-error">
        {{ message }}
        <template v-if="requestNewLink">
          <NuxtLink to="/forgot-password" class="alert-link d-block mt-1">Get a new link</NuxtLink>
        </template>
      </BAlert>

      <BForm v-if="token && !requestNewLink" method="post" novalidate @submit.prevent="onSubmit">
        <FormTextField
          id="password"
          v-model="form.password"
          label="New password"
          type="password"
          autocomplete="new-password"
          hint="At least 8 characters."
          autofocus
          :errors="fieldErrors.password"
        />
        <BButton
          type="submit"
          variant="primary"
          class="w-100 mt-2"
          :disabled="submitting || !hydrated"
        >
          {{ submitting ? 'Saving…' : 'Change my password' }}
        </BButton>
      </BForm>
    </template>
  </section>
</template>

<style scoped>
.auth-card {
  max-width: 26rem;
}
</style>
