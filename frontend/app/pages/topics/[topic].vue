<script setup lang="ts">
import type { components } from '~/types/api'

type TopicPage = components['schemas']['TopicPageResponse']

const route = useRoute()
const { $api } = useNuxtApp()
const { user } = useAuth()

const param = parseTopicParam(route.params.topic)
if (!param) throw createError({ statusCode: 404, statusMessage: 'Topic not found', fatal: true })

const page = computed(() => parsePage(route.query.page))
const { data, error } = await useAsyncData(
  () => `topic-${param.id}-${page.value}`,
  () => $api<TopicPage>(`/api/topics/${param.id}`, { query: { page: page.value } }),
  { watch: [page] },
)
if (error.value) {
  const status = parseProblem(error.value).status
  throw createError({
    statusCode: status === 404 ? 404 : 500,
    statusMessage: status === 404 ? 'Topic not found' : 'The topic could not be loaded',
    fatal: true,
  })
}

// The slug is only decoration: a stale or missing one is sent to the right address (permanent).
const topic = computed(() => data.value?.topic)
if (topic.value && param.slug !== topic.value.slug) {
  await navigateTo(
    { path: topicPath(topic.value.id, topic.value.slug), query: route.query },
    { redirectCode: 301, replace: true },
  )
}

// A removed topic is gone for search engines too (FR-5.7).
if (data.value?.removed && import.meta.server) {
  setResponseStatus(useRequestEvent()!, 410)
}

useSeoMeta({
  title: () => (data.value?.removed && !topic.value ? 'Topic removed' : topic.value?.title),
  robots: () => (data.value?.removed ? 'noindex' : undefined),
})

const locked = computed(() => topic.value?.locked === true)
</script>

<template>
  <section v-if="data">
    <div v-if="data.removed" class="mb-4" data-testid="removed-topic">
      <h1 class="h3">This topic has been removed</h1>
      <p class="text-body-secondary">
        It is no longer available.
        <NuxtLink :to="`/categories/${data.category.slug}`"
          >Back to {{ data.category.name }}</NuxtLink
        >
      </p>
      <template v-if="data.recentTopics.length">
        <h2 class="h5 mt-4">Recently active in {{ data.category.name }}</h2>
        <ul class="list-group mb-4">
          <TopicRow v-for="recent in data.recentTopics" :key="recent.id" :topic="recent" />
        </ul>
      </template>
    </div>

    <template v-if="topic && data.posts">
      <BAlert v-if="data.removed" :model-value="true" variant="danger">
        This topic was removed. Only admins can see it.
      </BAlert>

      <header class="mb-4">
        <p class="small mb-1">
          <NuxtLink :to="`/categories/${data.category.slug}`">{{ data.category.name }}</NuxtLink>
        </p>
        <h1 class="h3 mb-1">
          <i v-if="topic.pinned" class="bi bi-pin-angle-fill me-1" title="Pinned" />
          <i v-if="topic.locked" class="bi bi-lock-fill me-1" title="Locked" />
          {{ topic.title }}
        </h1>
        <p class="small text-body-secondary mb-0">
          {{ topic.replyCount }} {{ topic.replyCount === 1 ? 'reply' : 'replies' }}
          <template v-if="data.posts.totalPages > 1">
            ·
            <NuxtLink :to="{ path: route.path, query: { page: data.posts.totalPages } }">
              Jump to the last page
            </NuxtLink>
          </template>
        </p>
      </header>

      <PostCard v-for="post in data.posts.items" :key="post.id" :post="post" />

      <PaginationNav :page="data.posts.page" :total-pages="data.posts.totalPages" class="mb-4" />

      <BAlert v-if="locked" :model-value="true" variant="secondary" data-testid="locked-notice">
        <i class="bi bi-lock-fill me-1" aria-hidden="true" />
        This topic is locked. Nobody can reply to it.
      </BAlert>
      <p v-else-if="!user" class="text-body-secondary" data-testid="login-to-reply">
        <NuxtLink :to="`/login?redirect=${encodeURIComponent(route.fullPath)}`">Log in</NuxtLink>
        or <NuxtLink to="/register">sign up</NuxtLink> to reply.
      </p>
    </template>
  </section>
</template>
