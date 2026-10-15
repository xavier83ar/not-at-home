<script setup lang="ts">
import type { components } from '~/types/api'

type AppInfo = components['schemas']['AppInfo']

const { $api } = useNuxtApp()
const { data: info, error } = await useAsyncData('backend-info', () => $api<AppInfo>('/api/info'))

useSeoMeta({ title: 'Home' })
</script>

<template>
  <section class="text-center py-5">
    <img
      class="logo-light mb-4"
      src="/brand/logo-full.svg"
      alt="Not at Home: stories from the trail"
      width="260"
    />
    <img
      class="logo-dark mb-4"
      src="/brand/logo-full-dark.svg"
      alt="Not at Home: stories from the trail"
      width="260"
    />
    <h1 class="visually-hidden">Not at Home</h1>
    <p v-if="info" class="lead mb-0" data-testid="backend-info">
      Connected to <strong>{{ info.name }}</strong> {{ info.version }}
    </p>
    <BAlert v-else-if="error" :model-value="true" variant="warning">
      The backend is not reachable.
    </BAlert>
  </section>
</template>
