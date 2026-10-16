<script setup lang="ts">
import type { components } from '~/types/api'

type CategoryResponse = components['schemas']['CategoryResponse']

const { $api } = useNuxtApp()
const { data: categories, error } = await useAsyncData('categories', () =>
  $api<CategoryResponse[]>('/api/categories'),
)

useSeoMeta({
  title: 'Categories',
  description: 'Browse every category on Not at Home.',
})
</script>

<template>
  <section>
    <h1 class="h3 mb-4">Categories</h1>

    <BAlert v-if="error" :model-value="true" variant="warning">
      Categories could not be loaded. Please try again later.
    </BAlert>

    <p v-else-if="categories?.length === 0" class="text-body-secondary">
      There are no categories yet.
    </p>

    <div v-else class="row g-4">
      <div v-for="category in categories" :key="category.id" class="col-12 col-md-6 col-lg-4">
        <NuxtLink :to="`/categories/${category.slug}`" class="text-decoration-none">
          <BCard class="h-100 category-card" no-body>
            <div
              class="category-cover"
              :style="
                category.coverImageUrl ? { backgroundImage: `url(${category.coverImageUrl})` } : {}
              "
            />
            <BCardBody>
              <BCardTitle class="h5 mb-2 text-body">{{ category.name }}</BCardTitle>
              <p class="text-body-secondary mb-2">{{ category.description }}</p>
              <p class="small text-body-secondary mb-0">
                {{ category.topicCount }} {{ category.topicCount === 1 ? 'topic' : 'topics' }} ·
                {{ category.postCount }} {{ category.postCount === 1 ? 'post' : 'posts' }}
                <template v-if="category.latestActivityAt">
                  · active <TimeAgo :date="category.latestActivityAt" />
                </template>
              </p>
            </BCardBody>
          </BCard>
        </NuxtLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.category-card {
  transition: box-shadow 0.15s ease-in-out;
}

.category-card:hover {
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.1);
}

.category-cover {
  height: 8rem;
  background-color: var(--bs-secondary-bg);
  background-size: cover;
  background-position: center;
  border-radius: var(--bs-card-inner-border-radius) var(--bs-card-inner-border-radius) 0 0;
}
</style>
