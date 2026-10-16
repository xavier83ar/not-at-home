<script setup lang="ts">
import { computed } from 'vue'
import { initials } from '~/utils/initials'

/** The user's picture, or their initials on a Pine circle when they have none (FR-8.2). */
const props = withDefaults(defineProps<{ name: string; src?: string | null; size?: number }>(), {
  src: null,
  size: 40,
})

const dimension = computed(() => `${props.size}px`)
</script>

<template>
  <img
    v-if="src"
    :src="src"
    :alt="`${name}'s avatar`"
    class="avatar rounded-circle"
    :width="size"
    :height="size"
  />
  <span v-else class="avatar avatar-initials rounded-circle" aria-hidden="true">{{
    initials(name)
  }}</span>
</template>

<style scoped>
.avatar {
  width: v-bind(dimension);
  height: v-bind(dimension);
  flex-shrink: 0;
  object-fit: cover;
}

.avatar-initials {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bs-primary);
  color: var(--bs-white, #fff);
  font-weight: 600;
  font-size: 0.9rem;
}

[data-bs-theme='dark'] .avatar-initials {
  color: #111;
}
</style>
