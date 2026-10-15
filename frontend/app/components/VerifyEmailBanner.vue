<script setup lang="ts">
const { user, resendVerification } = useAuth()
const route = useRoute()

// The verify page explains the same thing itself.
const visible = computed(
  () => user.value !== null && !user.value.emailVerified && route.path !== '/verify-email',
)

const status = ref<'idle' | 'sending' | 'sent' | 'error'>('idle')

async function onResend() {
  status.value = 'sending'
  try {
    await resendVerification()
    status.value = 'sent'
  } catch {
    status.value = 'error'
  }
}
</script>

<template>
  <div v-if="visible" class="bg-warning-subtle border-bottom" data-testid="verify-banner">
    <div class="container py-2 d-flex flex-wrap align-items-center gap-2 justify-content-center">
      <span v-if="status === 'sent'" role="status">
        Email sent to <strong>{{ user?.email }}</strong
        >. Check your inbox (and spam folder).
      </span>
      <span v-else-if="status === 'error'" role="alert">
        Could not send the email. Please try again in a moment.
      </span>
      <span v-else>
        Verify your email address to start posting. We sent a link to
        <strong>{{ user?.email }}</strong
        >.
      </span>
      <BButton
        v-if="status !== 'sent'"
        variant="link"
        size="sm"
        class="p-0 align-baseline fw-semibold text-body"
        :disabled="status === 'sending'"
        @click="onResend"
      >
        {{ status === 'sending' ? 'Sending…' : 'Resend email' }}
      </BButton>
    </div>
  </div>
</template>
