<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { absoluteDateUtc, fullDate, relativeTime } from '~/utils/relativeTime'

/**
 * A date shown as "2 hours ago" with the full date on hover, in the visitor's time zone (NFR-7).
 * The server doesn't know either, so it renders a fixed UTC date and the browser swaps in the
 * relative text once mounted; both start from the same text, so hydration matches.
 */
const props = defineProps<{ date: string }>()

const text = ref(absoluteDateUtc(props.date))
const title = ref(absoluteDateUtc(props.date))

onMounted(() => {
  text.value = relativeTime(props.date)
  title.value = fullDate(props.date)
})
</script>

<template>
  <time :datetime="date" :title="title">{{ text }}</time>
</template>
