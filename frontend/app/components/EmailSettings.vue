<script setup lang="ts">
/**
 * Changing the email address (FR-8.3). The link goes to the new address, and the account keeps
 * the current one until it is used, so the page shows both while a change is waiting.
 */
const { $api } = useNuxtApp()
const { user, fetchUser } = useAuth()

// See login.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const form = reactive({ email: '', currentPassword: '' })
const submitting = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  submitting.value = true
  message.value = undefined
  fieldErrors.value = {}
  try {
    await $api('/api/me/email-change', { method: 'POST', body: { ...form } })
    form.email = ''
    form.currentPassword = ''
    // The waiting address comes with the current user.
    await fetchUser()
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
  <section v-if="user" aria-labelledby="email-heading">
    <h2 id="email-heading" class="h4 mb-3">Email address</h2>

    <p>
      Your email address is <strong>{{ user.email }}</strong
      >.
      <span v-if="user.emailVerified" class="badge text-bg-success">Verified</span>
      <span v-else class="badge text-bg-warning">Not verified</span>
      <span class="d-block small text-body-secondary">It is never shown publicly.</span>
    </p>

    <BAlert :model-value="!!user.pendingEmail" variant="info" data-testid="email-pending">
      We sent a link to <strong>{{ user.pendingEmail }}</strong
      >. Your address stays the same until you open it. The link works for 24 hours.
    </BAlert>
    <BAlert :model-value="!!message" variant="danger" data-testid="email-error">
      {{ message }}
    </BAlert>

    <BForm method="post" novalidate @submit.prevent="onSubmit">
      <FormTextField
        id="email-new"
        v-model="form.email"
        label="New email"
        type="email"
        autocomplete="email"
        hint="We'll send a link to confirm it. Your current address keeps working until then."
        :errors="fieldErrors.email"
      />
      <FormTextField
        id="email-password"
        v-model="form.currentPassword"
        label="Current password"
        type="password"
        autocomplete="current-password"
        :errors="fieldErrors.currentPassword"
      />
      <BButton type="submit" variant="primary" :disabled="submitting || !hydrated">
        {{ submitting ? 'Sending…' : 'Change email' }}
      </BButton>
    </BForm>
  </section>
</template>
