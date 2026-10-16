<script setup lang="ts">
import { sanitizeInline } from '~/utils/sanitize'

type ListItem = {
  content: string
  meta?: { checked?: boolean }
  items?: ListItem[]
}

const props = defineProps<{
  listType: string
  items: ListItem[]
  meta?: { start?: number; counterType?: string }
}>()

const LIST_STYLE_TYPES: Record<string, string> = {
  numeric: 'decimal',
  'lower-roman': 'lower-roman',
  'upper-roman': 'upper-roman',
  'lower-alpha': 'lower-alpha',
  'upper-alpha': 'upper-alpha',
}

const ordered = props.listType === 'ordered'
const checklist = props.listType === 'checklist'
const listStyleType = ordered ? LIST_STYLE_TYPES[props.meta?.counterType ?? ''] : undefined
</script>

<template>
  <component
    :is="ordered ? 'ol' : 'ul'"
    :start="ordered ? meta?.start : undefined"
    :class="{ 'list-unstyled': checklist }"
    :style="listStyleType ? { listStyleType } : undefined"
  >
    <li v-for="(item, index) in items" :key="index">
      <template v-if="checklist">
        <input
          type="checkbox"
          class="form-check-input me-2"
          :checked="item.meta?.checked === true"
          disabled
        />
        <!-- eslint-disable vue/no-v-html -- sanitized by sanitizeInline -->
        <span
          :class="{ 'text-decoration-line-through': item.meta?.checked }"
          v-html="sanitizeInline(item.content)"
        />
      </template>
      <span v-else v-html="sanitizeInline(item.content)" />
      <!-- eslint-enable vue/no-v-html -->
      <EditorList
        v-if="item.items?.length"
        :list-type="listType"
        :items="item.items"
        :meta="meta"
      />
    </li>
  </component>
</template>
