<script setup lang="ts">
import type { components } from '~/types/api'
import { postPath } from '~/utils/topicUrl'

type PostResponse = components['schemas']['PostResponse']

/**
 * One post in a topic (FR-4.2). A removed post shows a placeholder (FR-5.6); admins also get its
 * content, labelled as removed (FR-5.8).
 */
defineProps<{ post: PostResponse }>()
</script>

<template>
  <article :id="`post-${post.id}`" class="card mb-3 post" :class="{ 'post-removed': post.removed }">
    <div class="card-header d-flex align-items-center gap-2 bg-transparent">
      <UserAvatar :name="post.author.displayName" :src="post.author.avatarUrl" :size="32" />
      <div class="d-flex flex-column flex-sm-row align-items-sm-center column-gap-2 me-auto">
        <span class="fw-semibold">{{ post.author.displayName }}</span>
        <span class="small text-body-secondary">@{{ post.author.username }}</span>
      </div>
      <span v-if="post.author.role === 'ADMIN'" class="badge text-bg-accent">Admin</span>
      <span v-if="post.opening" class="badge text-bg-secondary">Original post</span>
      <NuxtLink
        :to="postPath(post.id)"
        class="small text-body-secondary text-decoration-none"
        title="Link to this post"
      >
        <TimeAgo :date="post.createdAt" />
      </NuxtLink>
    </div>
    <div class="card-body">
      <p v-if="post.removed && !post.content" class="text-body-secondary fst-italic mb-0">
        This post was removed.
      </p>
      <template v-else>
        <p v-if="post.removed" class="small text-danger-emphasis mb-3" data-testid="removed-label">
          <i class="bi bi-eye-slash me-1" aria-hidden="true" />
          Removed post. Only admins can see this content.
        </p>
        <EditorContent :document="post.content" />
        <p v-if="post.editedAt" class="small text-body-secondary mt-3 mb-0">
          Edited <TimeAgo :date="post.editedAt" />
        </p>
      </template>
    </div>
  </article>
</template>

<style scoped>
.post-removed {
  border-style: dashed;
}
</style>
