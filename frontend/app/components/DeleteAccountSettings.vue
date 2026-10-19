<script setup lang="ts">
/**
 * Deleting the account (FR-8.4): confirmed with the password and, because it can't be undone, a
 * second time in a dialog. Afterwards nobody is logged in here any more.
 */
const { $api } = useNuxtApp()
const { user } = useAuth()

// See login.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const form = reactive({ password: '' })
const submitting = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  if (!window.confirm('Delete your account? This can’t be undone.')) return
  submitting.value = true
  message.value = undefined
  fieldErrors.value = {}
  try {
    await $api('/api/me/delete-account', { method: 'POST', body: { ...form } })
  } catch (err) {
    const problem = parseProblem(err)
    fieldErrors.value = problem.fieldErrors
    message.value = accountErrorMessage(problem)
    submitting.value = false
    return
  }
  // The backend ended every session, this one's included.
  user.value = null
  await navigateTo('/')
}
</script>

<template>
  <section aria-labelledby="delete-heading" class="card border-danger">
    <div class="card-body">
      <h2 id="delete-heading" class="h4 text-danger">Delete account</h2>
      <p>This can't be undone. Your account is closed and you are logged out everywhere.</p>
      <ul>
        <li>Your posts are deleted, and topics you started that nobody replied to go with them.</li>
        <li>
          Topics you started that have replies stay, with “This post was removed” in place of your
          first post.
        </li>
        <li>Your name shows as “Deleted user” wherever it appeared.</li>
        <li>Your username and email address can be used to sign up again.</li>
      </ul>

      <BAlert :model-value="!!message" variant="danger" data-testid="delete-error">
        {{ message }}
      </BAlert>

      <BForm method="post" novalidate @submit.prevent="onSubmit">
        <FormTextField
          id="delete-password"
          v-model="form.password"
          label="Your password"
          type="password"
          autocomplete="current-password"
          hint="Enter it to confirm that you want to delete your account."
          :errors="fieldErrors.password"
        />
        <BButton type="submit" variant="danger" :disabled="submitting || !hydrated">
          {{ submitting ? 'Deleting…' : 'Delete my account' }}
        </BButton>
      </BForm>
    </div>
  </section>
</template>
