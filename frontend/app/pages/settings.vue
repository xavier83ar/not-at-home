<script setup lang="ts">
import type { components } from '~/types/api'

type UserProfile = components['schemas']['UserProfileResponse']

definePageMeta({ middleware: 'auth' })
useSeoMeta({ title: 'Settings', robots: 'noindex' })

const { $api } = useNuxtApp()
const { user } = useAuth()

// The public profile holds everything the form starts from, so there is no endpoint just for this.
const { data: profile, error } = await useAsyncData('settings-profile', () =>
  $api<UserProfile>(`/api/users/${encodeURIComponent(user.value!.username)}`),
)
</script>

<template>
  <section class="settings mx-auto py-4">
    <h1 class="h3 mb-4">Settings</h1>

    <BAlert v-if="error || !profile" :model-value="true" variant="warning">
      Your settings could not be loaded. Please try again later.
    </BAlert>
    <ProfileSettings v-else :profile="profile" />
  </section>
</template>

<style scoped>
.settings {
  max-width: 44rem;
}
</style>
