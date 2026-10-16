<script setup lang="ts">
import type { components } from '~/types/api'

type CategoryResponse = components['schemas']['CategoryResponse']
type TopicsPage = components['schemas']['PageResponseTopicSummary']

const route = useRoute()
const { $api } = useNuxtApp()
const slug = String(route.params.slug)
const page = computed(() => parsePage(route.query.page))

const { data: category, error: categoryError } = await useAsyncData(`category-${slug}`, () =>
  $api<CategoryResponse>(`/api/categories/${encodeURIComponent(slug)}`),
)
if (categoryError.value) {
  throw createError({
    statusCode: parseProblem(categoryError.value).status === 404 ? 404 : 500,
    statusMessage:
      parseProblem(categoryError.value).status === 404
        ? 'Category not found'
        : 'The category could not be loaded',
    fatal: true,
  })
}

const { data: topics, error } = await useAsyncData(
  () => `category-${slug}-topics-${page.value}`,
  () =>
    $api<TopicsPage>(`/api/categories/${encodeURIComponent(slug)}/topics`, {
      query: { page: page.value },
    }),
  { watch: [page] },
)
// A page past the end is a 404 for search engines too (the backend answers it that way).
if (error.value && parseProblem(error.value).status === 404) {
  throw createError({ statusCode: 404, statusMessage: 'Page not found', fatal: true })
}

useSeoMeta({
  title: () => (category.value ? category.value.name : 'Category'),
  description: () => category.value?.description,
})
</script>

<template>
  <section v-if="category">
    <BreadcrumbNav
      :crumbs="[{ label: 'Categories', to: '/categories' }, { label: category.name }]"
    />
    <header class="mb-4 d-flex flex-wrap justify-content-between align-items-start gap-3">
      <div>
        <h1 class="h3 mb-1">{{ category.name }}</h1>
        <p class="text-body-secondary mb-1">{{ category.description }}</p>
        <p class="small text-body-secondary mb-0">
          {{ category.topicCount }} {{ category.topicCount === 1 ? 'topic' : 'topics' }} ·
          {{ category.postCount }} {{ category.postCount === 1 ? 'post' : 'posts' }}
        </p>
      </div>
      <NuxtLink :to="`/topics/new?category=${category.slug}`" class="btn btn-accent">
        New topic
      </NuxtLink>
    </header>

    <BAlert v-if="error" :model-value="true" variant="warning">
      Topics could not be loaded. Please try again later.
    </BAlert>

    <p v-else-if="topics?.items.length === 0" class="text-body-secondary">
      No topics here yet. Be the first to start one.
    </p>

    <template v-else-if="topics">
      <ul class="list-group mb-4">
        <TopicRow v-for="topic in topics.items" :key="topic.id" :topic="topic" />
      </ul>
      <PaginationNav :page="topics.page" :total-pages="topics.totalPages" />
    </template>
  </section>
</template>
