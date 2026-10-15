<script setup lang="ts">
useSeoMeta({ title: 'Verify your email', robots: 'noindex' })
// The token is in the URL: don't pass it on to anything the page links to or loads.
useHead({ meta: [{ name: 'referrer', content: 'no-referrer' }] })

const route = useRoute()
const { user, isLoggedIn, verifyEmail, resendVerification } = useAuth()

const token = computed(() => firstQueryValue(route.query.token))

// The link only asks for confirmation here; the button does the work. Mail scanners and link
// previews open links on their own, and they must not use the token up.
type State = 'ready' | 'verifying' | 'verified' | 'failed'
const state = ref<State>(token.value ? 'ready' : 'failed')
const failure = ref(
  token.value ? '' : 'This link is missing its verification code. Open it again from the email.',
)

const resendState = ref<'idle' | 'sending' | 'sent' | 'error'>('idle')
const resendError = ref('')

async function onVerify() {
  if (!token.value) return
  state.value = 'verifying'
  try {
    await verifyEmail(token.value)
    state.value = 'verified'
  } catch (error) {
    failure.value = verifyErrorMessage(parseProblem(error))
    state.value = 'failed'
  }
}

async function onResend() {
  resendState.value = 'sending'
  try {
    await resendVerification()
    resendState.value = 'sent'
  } catch (error) {
    resendError.value = resendErrorMessage(parseProblem(error))
    resendState.value = 'error'
  }
}
</script>

<template>
  <section class="auth-card mx-auto py-4 text-center">
    <h1 class="h3 mb-4">Verify your email</h1>

    <template v-if="state === 'ready' || state === 'verifying'">
      <p>Confirm your email address to start posting in the forum.</p>
      <BButton
        variant="primary"
        :disabled="state === 'verifying'"
        data-testid="confirm-email"
        @click="onVerify"
      >
        {{ state === 'verifying' ? 'Confirming…' : 'Confirm my email address' }}
      </BButton>
    </template>

    <template v-else-if="state === 'verified'">
      <BAlert :model-value="true" variant="success" data-testid="verified">
        Your email address is verified. Thanks!
      </BAlert>
      <NuxtLink v-if="isLoggedIn" to="/" class="btn btn-primary">Go to the forum</NuxtLink>
      <NuxtLink v-else to="/login" class="btn btn-primary">Log in</NuxtLink>
    </template>

    <template v-else>
      <BAlert :model-value="true" variant="danger" data-testid="verify-error">
        {{ failure }}
      </BAlert>
      <template v-if="user && !user.emailVerified">
        <p v-if="resendState === 'sent'" role="status" data-testid="resent">
          A new link is on its way to {{ user.email }}.
        </p>
        <BButton v-else variant="primary" :disabled="resendState === 'sending'" @click="onResend">
          Send me a new link
        </BButton>
        <p v-if="resendState === 'error'" class="text-danger mt-2" role="alert">
          {{ resendError }}
        </p>
      </template>
      <p v-else-if="!isLoggedIn">
        <NuxtLink to="/login?redirect=/verify-email">Log in</NuxtLink> to get a new link.
      </p>
    </template>
  </section>
</template>

<style scoped>
.auth-card {
  max-width: 26rem;
}
</style>
