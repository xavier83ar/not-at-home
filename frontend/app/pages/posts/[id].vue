<script setup lang="ts">
import type { components } from '~/types/api'

type PostLocation = components['schemas']['PostLocation']

/**
 * A post's permanent link (FR-4.6): finds its topic and the page it is on, and sends the visitor
 * there, scrolled to the post. Posts never change page, so the redirect is permanent.
 */
const route = useRoute()
const { $api } = useNuxtApp()
const id = Number(route.params.id)

useSeoMeta({ robots: 'noindex' })

if (!Number.isInteger(id) || id <= 0) {
  throw createError({ statusCode: 404, statusMessage: 'Post not found', fatal: true })
}

let location: PostLocation
try {
  location = await $api<PostLocation>(`/api/posts/${id}/location`)
} catch (err) {
  const status = parseProblem(err).status
  throw createError({
    statusCode: status === 404 ? 404 : 500,
    statusMessage: status === 404 ? 'Post not found' : 'The post could not be loaded',
    fatal: true,
  })
}

await navigateTo(
  {
    path: topicPath(location.topicId, location.topicSlug),
    query: location.page > 1 ? { page: location.page } : {},
    hash: `#post-${location.postId}`,
  },
  { redirectCode: 301, replace: true },
)
</script>

<template>
  <p class="text-body-secondary">Taking you to the post…</p>
</template>
