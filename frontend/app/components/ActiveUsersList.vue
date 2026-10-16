<script setup lang="ts">
import type { components } from '~/types/api'
import UserAvatar from '~/components/UserAvatar.vue'

type ActiveUser = components['schemas']['ActiveUserResponse']

/**
 * The most active users of the last 30 days (section 8.2). Names are not links yet: public
 * profiles arrive with M7.
 */
defineProps<{ users: ActiveUser[] }>()
</script>

<template>
  <ol class="list-unstyled mb-0">
    <li v-for="entry in users" :key="entry.user.id" class="d-flex align-items-center gap-3 py-2">
      <UserAvatar :name="entry.user.displayName" :src="entry.user.avatarUrl" :size="36" />
      <span class="flex-grow-1 text-truncate fw-semibold">{{ entry.user.displayName }}</span>
      <span class="small text-body-secondary text-nowrap">
        {{ entry.postCount }} {{ entry.postCount === 1 ? 'post' : 'posts' }}
      </span>
    </li>
  </ol>
</template>
