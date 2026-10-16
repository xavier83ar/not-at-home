<script setup lang="ts">
import type { components } from '~/types/api'

type HomeResponse = components['schemas']['HomeResponse']

const { $api } = useNuxtApp()
const { user } = useAuth()
const { data: home, error } = await useAsyncData('home', () => $api<HomeResponse>('/api/home'))

const isAdmin = computed(() => user.value?.role === 'ADMIN')

useSeo({
  title: 'Stories from the trail',
  description:
    'Not at Home is a forum for outdoor enthusiasts: share trips, ask questions and talk gear.',
  path: '/',
})
</script>

<template>
  <div>
    <h1 class="visually-hidden">Not at Home: stories from the trail</h1>

    <BAlert v-if="error" :model-value="true" variant="warning">
      The forum could not be loaded. Please try again later.
    </BAlert>

    <template v-else-if="home">
      <!-- A fresh forum: nothing but the invitation to set it up. -->
      <section
        v-if="home.highlightedCategories.length === 0"
        class="text-center py-5"
        data-testid="empty-forum"
      >
        <h2 class="h3">Welcome to Not at Home</h2>
        <template v-if="isAdmin">
          <p class="text-body-secondary">The forum has no categories yet. Create the first one.</p>
          <NuxtLink to="/admin/categories/new" class="btn btn-accent">Create a category</NuxtLink>
        </template>
        <p v-else class="text-body-secondary mb-0">
          The forum is being set up. Come back soon for stories from the trail.
        </p>
      </section>

      <template v-else>
        <section aria-labelledby="home-categories" class="mb-5">
          <div class="d-flex justify-content-between align-items-baseline mb-3">
            <h2 id="home-categories" class="h4 mb-0">Categories</h2>
            <NuxtLink to="/categories">All categories</NuxtLink>
          </div>
          <div class="row g-3">
            <div
              v-for="category in home.highlightedCategories"
              :key="category.id"
              class="col-12 col-sm-6 col-lg-3"
            >
              <HomeCategoryCard :category="category" />
            </div>
          </div>
        </section>

        <div class="row g-5">
          <section class="col-12 col-lg-8" aria-labelledby="home-topics">
            <h2 id="home-topics" class="h4 mb-3">Latest topics</h2>
            <p v-if="home.latestTopics.length === 0" class="text-body-secondary">
              No topics yet. Be the first to start one.
              <NuxtLink to="/topics/new">New topic</NuxtLink>
            </p>
            <template v-else>
              <ul class="list-group mb-3">
                <TopicRow
                  v-for="topic in home.latestTopics"
                  :key="topic.id"
                  :topic="topic"
                  show-category
                />
              </ul>
              <NuxtLink to="/topics">View all topics</NuxtLink>
            </template>
          </section>

          <section class="col-12 col-lg-4" aria-labelledby="home-users">
            <h2 id="home-users" class="h4 mb-1">Most active</h2>
            <p class="small text-body-secondary">Who posted the most in the last 30 days.</p>
            <p v-if="home.activeUsers.length === 0" class="text-body-secondary">
              Nobody has posted recently.
            </p>
            <ActiveUsersList v-else :users="home.activeUsers" />
          </section>
        </div>
      </template>
    </template>
  </div>
</template>
