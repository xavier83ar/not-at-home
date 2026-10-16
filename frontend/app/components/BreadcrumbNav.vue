<script setup lang="ts">
import { computed } from 'vue'

export interface Crumb {
  label: string
  /** Missing on the current page, which is the last crumb. */
  to?: string
}

/**
 * Home › Category › Topic (NFR-1). "Home" is always the first crumb; pass the rest. Plain links,
 * so it works without JavaScript. The last crumb is the current page and is not a link.
 */
const props = defineProps<{ crumbs: Crumb[] }>()

const all = computed<Crumb[]>(() => [{ label: 'Home', to: '/' }, ...props.crumbs])
</script>

<template>
  <nav aria-label="Breadcrumb" class="mb-3">
    <ol class="breadcrumb small mb-0">
      <li
        v-for="(crumb, index) in all"
        :key="index"
        class="breadcrumb-item"
        :class="{ active: index === all.length - 1 }"
        :aria-current="index === all.length - 1 ? 'page' : undefined"
      >
        <NuxtLink v-if="crumb.to && index < all.length - 1" :to="crumb.to">{{
          crumb.label
        }}</NuxtLink>
        <span v-else class="crumb-current">{{ crumb.label }}</span>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
/* A long topic title must not push the trail onto several lines. */
.crumb-current {
  display: inline-block;
  max-width: 24rem;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

@media (max-width: 575.98px) {
  .crumb-current {
    max-width: 10rem;
  }
}
</style>
