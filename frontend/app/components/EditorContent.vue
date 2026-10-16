<script setup lang="ts">
import { computed } from 'vue'
import type { components } from '~/types/api'
import EditorList from '~/components/EditorList.vue'
import { inlineText, sanitizeInline } from '~/utils/sanitize'

type EditorDocument = components['schemas']['EditorDocument']

/**
 * Shows a post: the Editor.js document turned into HTML. This is plain Vue, so it renders on the
 * server and the page is fully readable without JavaScript (section 4.3). Every piece of inline
 * HTML goes through `sanitizeInline`; block fields that are plain text (code, titles) are
 * interpolated, so Vue escapes them. Blocks of an unknown type are skipped.
 */
const props = defineProps<{ document: EditorDocument | null | undefined }>()

/* eslint-disable @typescript-eslint/no-explicit-any -- block data is checked by the backend */
type Data = Record<string, any>

const blocks = computed(() => (props.document?.blocks ?? []) as { type: string; data: Data }[])

function headerTag(data: Data): string {
  const level = Number(data.level)
  // Posts sit under the page's own h1 and the topic's h2, so their headings start at h3.
  return `h${Number.isInteger(level) ? Math.min(Math.max(level, 1) + 2, 6) : 4}`
}

function isSafeImage(data: Data): boolean {
  return typeof data.file?.url === 'string' && /^\/api\/uploads\/[\w.-]+$/.test(data.file.url)
}

function isWebUrl(url: unknown): url is string {
  return typeof url === 'string' && /^https?:\/\//i.test(url)
}
</script>

<template>
  <div class="editor-content">
    <template v-for="(block, index) in blocks" :key="index">
      <!-- eslint-disable vue/no-v-html -- every v-html below is sanitized by sanitizeInline -->
      <p v-if="block.type === 'paragraph'" v-html="sanitizeInline(String(block.data.text ?? ''))" />
      <component :is="headerTag(block.data)" v-else-if="block.type === 'header'">
        <span v-html="sanitizeInline(String(block.data.text ?? ''))" />
      </component>
      <EditorList
        v-else-if="block.type === 'list'"
        :list-type="String(block.data.style)"
        :items="(block.data.items as any) ?? []"
        :meta="block.data.meta as any"
      />
      <blockquote v-else-if="block.type === 'quote'" class="blockquote border-start ps-3">
        <p class="mb-1" v-html="sanitizeInline(String(block.data.text ?? ''))" />
        <footer
          v-if="block.data.caption"
          class="blockquote-footer mb-0"
          v-html="sanitizeInline(String(block.data.caption))"
        />
      </blockquote>
      <pre
        v-else-if="block.type === 'code'"
        class="border rounded p-3"
      ><code>{{ block.data.code }}</code></pre>
      <hr v-else-if="block.type === 'delimiter'" class="my-4 w-25 mx-auto" />
      <figure v-else-if="block.type === 'image' && isSafeImage(block.data)" class="figure d-block">
        <img
          :src="block.data.file.url"
          :alt="inlineText(String(block.data.caption ?? ''))"
          class="figure-img img-fluid rounded"
          loading="lazy"
        />
        <figcaption
          v-if="block.data.caption"
          class="figure-caption"
          v-html="sanitizeInline(String(block.data.caption))"
        />
      </figure>
      <p v-else-if="block.type === 'link' && isWebUrl(block.data.link)">
        <a :href="block.data.link" rel="nofollow noopener ugc" target="_blank">{{
          block.data.title || block.data.link
        }}</a>
      </p>
      <!-- eslint-enable vue/no-v-html -->
    </template>
  </div>
</template>

<style scoped>
.editor-content {
  overflow-wrap: anywhere;
}

.editor-content :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  background-color: var(--bs-tertiary-bg);
}

.editor-content :deep(> :last-child) {
  margin-bottom: 0;
}
</style>
