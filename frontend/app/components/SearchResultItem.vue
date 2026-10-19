<script setup lang="ts">
import type { components } from '~/types/api'
import AuthorName from '~/components/AuthorName.vue'
import { sanitizeSnippet } from '~/utils/sanitize'
import { postPath } from '~/utils/topicUrl'

type SearchResult = components['schemas']['SearchResult']

/**
 * One matching post (FR-6.2): the topic it is in, linked to the post's permanent link (FR-4.6), a
 * snippet with the matched words marked, and who wrote it, where and when.
 */
defineProps<{ result: SearchResult }>()
</script>

<template>
  <li class="list-group-item py-3">
    <NuxtLink :to="postPath(result.postId)" class="fw-semibold">{{ result.topicTitle }}</NuxtLink>
    <!-- eslint-disable-next-line vue/no-v-html -- sanitizeSnippet keeps nothing but <mark> -->
    <p class="search-snippet mb-1" v-html="sanitizeSnippet(result.snippet)" />
    <div class="small text-body-secondary d-flex flex-wrap align-items-center gap-1">
      <UserAvatar :name="result.author.displayName" :src="result.author.avatarUrl" :size="20" />
      <AuthorName :author="result.author" />
      <span aria-hidden="true">·</span>
      <NuxtLink :to="`/categories/${result.category.slug}`" class="text-body-secondary">{{
        result.category.name
      }}</NuxtLink>
      <span aria-hidden="true">·</span>
      <TimeAgo :date="result.createdAt" />
    </div>
  </li>
</template>

<style scoped>
.search-snippet {
  overflow-wrap: anywhere;
}

/* Bootstrap's side padding on <mark> leaves visible gaps around a match in running text. */
.search-snippet :deep(mark) {
  padding: 0 0.1em;
}
</style>
