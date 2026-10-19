<script setup lang="ts">
import type { components } from '~/types/api'
import { postPath } from '~/utils/topicUrl'

type ProfilePost = components['schemas']['ProfilePost']

/** One of a user's recent posts on their profile (FR-8.1), linked to the post's permanent link. */
defineProps<{ post: ProfilePost }>()
</script>

<template>
  <li class="list-group-item py-3">
    <NuxtLink :to="postPath(post.postId)" class="fw-semibold">{{ post.topicTitle }}</NuxtLink>
    <p class="post-excerpt mb-1">{{ post.excerpt }}</p>
    <div class="small text-body-secondary">
      in
      <NuxtLink :to="`/categories/${post.category.slug}`" class="text-body-secondary">{{
        post.category.name
      }}</NuxtLink>
      <span aria-hidden="true"> · </span>
      <TimeAgo :date="post.createdAt" />
    </div>
  </li>
</template>

<style scoped>
.post-excerpt {
  overflow-wrap: anywhere;
}
</style>
