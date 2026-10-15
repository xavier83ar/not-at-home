<script setup lang="ts">
useHead({ titleTemplate: (title) => (title ? `${title} · Not at Home` : 'Not at Home') })
</script>

<template>
  <div class="d-flex flex-column flex-grow-1">
    <NuxtRouteAnnouncer />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </div>
</template>
