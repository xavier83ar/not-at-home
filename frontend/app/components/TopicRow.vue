<script setup lang="ts">
import type { components } from '~/types/api'
import { postPath, topicPath } from '~/utils/topicUrl'

type TopicSummary = components['schemas']['TopicSummary']

/**
 * One topic in a list (FR-3.2); the activity time links to its latest post (FR-7.2). Lists that
 * mix categories (home, all topics) say which one each topic is in.
 */
defineProps<{ topic: TopicSummary; showCategory?: boolean }>()
</script>

<template>
  <li class="list-group-item d-flex align-items-center gap-3 py-3">
    <UserAvatar :name="topic.author.displayName" :src="topic.author.avatarUrl" />
    <div class="flex-grow-1 min-w-0">
      <div class="d-flex align-items-baseline gap-2">
        <i
          v-if="topic.pinned"
          class="bi bi-pin-angle-fill text-body-secondary"
          title="Pinned"
          aria-label="Pinned"
        />
        <i
          v-if="topic.locked"
          class="bi bi-lock-fill text-body-secondary"
          title="Locked"
          aria-label="Locked"
        />
        <NuxtLink :to="topicPath(topic.id, topic.slug)" class="fw-semibold text-truncate">
          {{ topic.title }}
        </NuxtLink>
      </div>
      <div class="small text-body-secondary">
        by <AuthorName :author="topic.author" />
        <template v-if="showCategory">
          · in
          <NuxtLink :to="`/categories/${topic.category.slug}`" class="text-body-secondary">{{
            topic.category.name
          }}</NuxtLink>
        </template>
      </div>
    </div>
    <div class="text-end small text-body-secondary text-nowrap">
      <div>{{ topic.replyCount }} {{ topic.replyCount === 1 ? 'reply' : 'replies' }}</div>
      <NuxtLink :to="postPath(topic.lastPostId)" class="text-body-secondary">
        <TimeAgo :date="topic.lastPostAt" />
      </NuxtLink>
    </div>
  </li>
</template>

<style scoped>
.min-w-0 {
  min-width: 0;
}
</style>
