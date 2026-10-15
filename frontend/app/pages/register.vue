<script setup lang="ts">
useSeoMeta({ title: 'Sign up', robots: 'noindex' })

const { isLoggedIn, register } = useAuth()

if (isLoggedIn.value) {
  await navigateTo('/')
}

// See login.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const form = reactive({ username: '', displayName: '', email: '', password: '' })
const submitting = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  submitting.value = true
  message.value = undefined
  fieldErrors.value = {}
  try {
    await register({ ...form })
    await navigateTo('/')
  } catch (error) {
    const problem = parseProblem(error)
    fieldErrors.value = problem.fieldErrors
    message.value = registerErrorMessage(problem)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-card mx-auto py-4">
    <h1 class="h3 mb-4">Create your account</h1>

    <BAlert :model-value="!!message" variant="danger" data-testid="register-error">
      {{ message }}
    </BAlert>

    <BForm method="post" novalidate @submit.prevent="onSubmit">
      <FormTextField
        id="username"
        v-model="form.username"
        label="Username"
        autocomplete="username"
        hint="3 to 30 characters: letters, digits, _ and -. You can't change it later."
        autofocus
        :errors="fieldErrors.username"
      />
      <FormTextField
        id="displayName"
        v-model="form.displayName"
        label="Display name"
        autocomplete="nickname"
        hint="The name people see on your posts. You can change it later."
        :errors="fieldErrors.displayName"
      />
      <FormTextField
        id="email"
        v-model="form.email"
        label="Email"
        type="email"
        autocomplete="email"
        hint="We'll send a link to confirm it. It is never shown publicly."
        :errors="fieldErrors.email"
      />
      <FormTextField
        id="password"
        v-model="form.password"
        label="Password"
        type="password"
        autocomplete="new-password"
        hint="At least 8 characters."
        :errors="fieldErrors.password"
      />

      <BButton
        type="submit"
        variant="primary"
        class="w-100 mt-2"
        :disabled="submitting || !hydrated"
      >
        {{ submitting ? 'Creating your account…' : 'Sign up' }}
      </BButton>
    </BForm>

    <p class="mt-4 mb-0 text-center">
      Already have an account? <NuxtLink to="/login">Log in</NuxtLink>
    </p>
  </section>
</template>

<style scoped>
.auth-card {
  max-width: 26rem;
}
</style>
