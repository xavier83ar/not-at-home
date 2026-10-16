<script setup lang="ts">
import type { components } from '~/types/api'

type TopicsPage = components['schemas']['PageResponseTopicSummary']

const route = useRoute()
const { $api } = useNuxtApp()
const page = computed(() => parsePage(route.query.page))

const { data: topics, error } = await useAsyncData(
  () => `topics-${page.value}`,
  () => $api<TopicsPage>('/api/topics', { query: { page: page.value } }),
  { watch: [page] },
)
// A page past the end is a 404 for search engines too (the backend answers it that way).
if (error.value && parseProblem(error.value).status === 404) {
  throw createError({ statusCode: 404, statusMessage: 'Page not found', fatal: true })
}

useSeoMeta({
  title: 'All topics',
  description: 'The latest discussions in every category of Not at Home.',
})
</script>

<template>
  <section>
    <BreadcrumbNav :crumbs="[{ label: 'All topics' }]" />
    <h1 class="h3 mb-4">All topics</h1>

    <BAlert v-if="error" :model-value="true" variant="warning">
      Topics could not be loaded. Please try again later.
    </BAlert>

    <p v-else-if="topics?.items.length === 0" class="text-body-secondary">
      No topics yet. Be the first to start one.
      <NuxtLink to="/topics/new">New topic</NuxtLink>
    </p>

    <template v-else-if="topics">
      <ul class="list-group mb-4">
        <TopicRow v-for="topic in topics.items" :key="topic.id" :topic="topic" show-category />
      </ul>
      <PaginationNav :page="topics.page" :total-pages="topics.totalPages" />
    </template>
  </section>
</template>
