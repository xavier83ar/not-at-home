<script setup lang="ts">
import { computed } from 'vue'
import { pageItems } from '~/utils/pagination'

/**
 * Page links for a list (FR-7.1). They are real links to `?page=n`, so every page is reachable
 * and indexable without JavaScript. Other query parameters are kept.
 */
const props = defineProps<{ page: number; totalPages: number }>()

const route = useRoute()
const items = computed(() => pageItems(props.page, props.totalPages))

function target(page: number) {
  const { page: _ignored, ...rest } = route.query
  return { path: route.path, query: page > 1 ? { ...rest, page } : rest }
}
</script>

<template>
  <nav v-if="totalPages > 1" aria-label="Pages">
    <ul class="pagination justify-content-center flex-wrap mb-0">
      <li class="page-item" :class="{ disabled: page <= 1 }">
        <NuxtLink v-if="page > 1" class="page-link" :to="target(page - 1)" rel="prev">
          Previous
        </NuxtLink>
        <span v-else class="page-link">Previous</span>
      </li>
      <template v-for="(item, index) in items" :key="index">
        <li v-if="item === null" class="page-item disabled">
          <span class="page-link">…</span>
        </li>
        <li v-else class="page-item" :class="{ active: item === page }">
          <NuxtLink
            class="page-link"
            :to="target(item)"
            :aria-current="item === page ? 'page' : undefined"
          >
            {{ item }}
          </NuxtLink>
        </li>
      </template>
      <li class="page-item" :class="{ disabled: page >= totalPages }">
        <NuxtLink v-if="page < totalPages" class="page-link" :to="target(page + 1)" rel="next">
          Next
        </NuxtLink>
        <span v-else class="page-link">Next</span>
      </li>
    </ul>
  </nav>
</template>
