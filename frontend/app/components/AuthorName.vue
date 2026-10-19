<script setup lang="ts">
import { profilePath } from '~/utils/profileUrl'

/**
 * Someone's display name, linked to their profile (FR-8.1). A deleted account has no profile page
 * (FR-8.4), so its "Deleted user" is plain text.
 */
defineProps<{ author: { username: string; displayName: string; deleted: boolean } }>()
</script>

<template>
  <span v-if="author.deleted">{{ author.displayName }}</span>
  <NuxtLink
    v-else
    :to="profilePath(author.username)"
    class="text-reset link-underline-opacity-0 link-underline-opacity-100-hover"
    >{{ author.displayName }}</NuxtLink
  >
</template>
