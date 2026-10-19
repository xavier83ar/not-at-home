<script setup lang="ts">
/**
 * Changing the password (FR-8.3): the current one is required, and every other device is logged
 * out, which the form says before it happens.
 */
const { $api } = useNuxtApp()

// See login.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const form = reactive({ currentPassword: '', newPassword: '' })
const submitting = ref(false)
const saved = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  submitting.value = true
  saved.value = false
  message.value = undefined
  fieldErrors.value = {}
  try {
    await $api('/api/me/password', { method: 'PUT', body: { ...form } })
    form.currentPassword = ''
    form.newPassword = ''
    saved.value = true
  } catch (err) {
    const problem = parseProblem(err)
    fieldErrors.value = problem.fieldErrors
    message.value = accountErrorMessage(problem)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section aria-labelledby="password-heading">
    <h2 id="password-heading" class="h4 mb-3">Password</h2>

    <BAlert :model-value="!!message" variant="danger" data-testid="password-error">
      {{ message }}
    </BAlert>
    <BAlert :model-value="saved" variant="success" data-testid="password-saved">
      Your password has been changed. You've been logged out on your other devices.
    </BAlert>

    <BForm method="post" novalidate @submit.prevent="onSubmit">
      <FormTextField
        id="password-current"
        v-model="form.currentPassword"
        label="Current password"
        type="password"
        autocomplete="current-password"
        :errors="fieldErrors.currentPassword"
      />
      <FormTextField
        id="password-new"
        v-model="form.newPassword"
        label="New password"
        type="password"
        autocomplete="new-password"
        hint="At least 8 characters. Changing it logs you out on all your other devices."
        :errors="fieldErrors.newPassword"
      />
      <BButton type="submit" variant="primary" :disabled="submitting || !hydrated">
        {{ submitting ? 'Saving…' : 'Change password' }}
      </BButton>
    </BForm>
  </section>
</template>
