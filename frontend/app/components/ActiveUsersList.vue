<script setup lang="ts">
import type { components } from '~/types/api'
import AuthorName from '~/components/AuthorName.vue'
import UserAvatar from '~/components/UserAvatar.vue'

type ActiveUser = components['schemas']['ActiveUserResponse']

/** The most active users of the last 30 days (section 8.2), each linked to their profile. */
defineProps<{ users: ActiveUser[] }>()
</script>

<template>
  <ol class="list-unstyled mb-0">
    <li v-for="entry in users" :key="entry.user.id" class="d-flex align-items-center gap-3 py-2">
      <UserAvatar :name="entry.user.displayName" :src="entry.user.avatarUrl" :size="36" />
      <AuthorName :author="entry.user" class="flex-grow-1 text-truncate fw-semibold" />
      <span class="small text-body-secondary text-nowrap">
        {{ entry.postCount }} {{ entry.postCount === 1 ? 'post' : 'posts' }}
      </span>
    </li>
  </ol>
</template>
