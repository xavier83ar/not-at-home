<script setup lang="ts">
import { computed } from 'vue'
import type { components } from '~/types/api'

type CategoryResponse = components['schemas']['CategoryResponse']

/** A highlighted category on the home page (section 8.2): its cover as the background. */
const props = defineProps<{ category: CategoryResponse }>()

const background = computed(() =>
  props.category.coverImageUrl ? { backgroundImage: `url(${props.category.coverImageUrl})` } : {},
)
</script>

<template>
  <NuxtLink :to="`/categories/${category.slug}`" class="home-card" :style="background">
    <span class="home-card-body">
      <span class="home-card-name">{{ category.name }}</span>
      <span class="home-card-count">
        {{ category.topicCount }} {{ category.topicCount === 1 ? 'topic' : 'topics' }}
      </span>
    </span>
  </NuxtLink>
</template>

<style scoped>
.home-card {
  display: flex;
  align-items: flex-end;
  min-height: 14rem;
  height: 100%;
  background-color: #1f4d3a;
  background-size: cover;
  background-position: center;
  border-radius: var(--bs-border-radius-lg);
  color: #fff;
  overflow: hidden;
  text-decoration: none;
  transition: transform 0.15s ease-in-out;
}

.home-card:hover {
  color: #fff;
  transform: translateY(-2px);
}

@media (min-width: 992px) {
  .home-card {
    min-height: 18rem;
  }
}

/* Keeps white text readable over any cover image. */
.home-card-body {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 3rem 1.25rem 1.25rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.75), rgba(0, 0, 0, 0));
}

.home-card-name {
  font-family: var(--bs-heading-font-family, inherit);
  font-size: 1.4rem;
  font-weight: 700;
  line-height: 1.2;
}

.home-card-count {
  font-size: 0.9rem;
  opacity: 0.9;
}
</style>
