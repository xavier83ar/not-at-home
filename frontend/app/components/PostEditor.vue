<script setup lang="ts">
import type EditorJS from '@editorjs/editorjs'
import type { components } from '~/types/api'

type EditorDocument = components['schemas']['EditorDocument']
type UploadResponse = components['schemas']['UploadResponse']

/**
 * The Editor.js editor for posts (FR-4.4). Editor.js only runs in the browser, so use this inside
 * `<ClientOnly>`; reading never needs it (see `EditorContent`). Enabled blocks: paragraph (with
 * bold, italic and links), header, list, quote, code, delimiter and image. Images are uploaded
 * through `/api/uploads` with the CSRF-aware `$api`; adding one by address is switched off, since
 * the backend only accepts images that were uploaded to it.
 */
const props = defineProps<{
  label: string
  placeholder?: string
  initial?: EditorDocument | null
}>()

const { $api } = useNuxtApp()
const holder = ref<HTMLElement>()
const uploadError = ref<string>()
/** False until Editor.js has loaded: saving earlier would read an editor that is not there. */
const ready = ref(false)
let editor: EditorJS | undefined

async function uploadByFile(file: Blob) {
  uploadError.value = undefined
  try {
    const body = new FormData()
    body.append('file', file)
    const uploaded = await $api<UploadResponse>('/api/uploads', { method: 'POST', body })
    return { success: 1, file: { url: uploaded.url } }
  } catch (err) {
    uploadError.value = uploadErrorMessage(parseProblem(err))
    return { success: 0, file: { url: '' } }
  }
}

async function uploadByUrl() {
  uploadError.value = 'Upload the image from your device: images from other sites are not accepted.'
  return { success: 0, file: { url: '' } }
}

onMounted(async () => {
  const [
    { default: EditorJSClass },
    { default: Header },
    { default: EditorjsList },
    { default: Quote },
    { default: Code },
    { default: Delimiter },
    { default: ImageTool },
  ] = await Promise.all([
    import('@editorjs/editorjs'),
    import('@editorjs/header'),
    import('@editorjs/list'),
    import('@editorjs/quote'),
    import('@editorjs/code'),
    import('@editorjs/delimiter'),
    import('@editorjs/image'),
  ])
  if (!holder.value) return

  editor = new EditorJSClass({
    holder: holder.value,
    placeholder: props.placeholder ?? 'Write something…',
    data: props.initial ? { blocks: props.initial.blocks as never } : undefined,
    tools: {
      header: {
        class: Header as never,
        inlineToolbar: true,
        config: { levels: [2, 3, 4], defaultLevel: 2 },
      },
      list: { class: EditorjsList as never, inlineToolbar: true },
      quote: {
        class: Quote as never,
        inlineToolbar: true,
        config: { quotePlaceholder: 'Quote', captionPlaceholder: 'Source' },
      },
      code: Code as never,
      delimiter: Delimiter as never,
      image: {
        class: ImageTool as never,
        config: {
          endpoints: {},
          types: 'image/jpeg,image/png,image/webp',
          features: { border: false, background: false, stretch: false, caption: 'optional' },
          uploader: { uploadByFile, uploadByUrl },
        },
      },
    },
  })
  await editor.isReady
  ready.value = true
})

onBeforeUnmount(() => {
  editor?.destroy()
  editor = undefined
})

/** The document to send to the backend, which cleans it again (blocks only: no ids, no times). */
async function save(): Promise<EditorDocument> {
  const output = await editor!.save()
  return { blocks: output.blocks.map(({ type, data }) => ({ type, data })) }
}

/** Editor.js leaves out blocks with nothing in them, so no blocks means nothing was written. */
async function isEmpty(): Promise<boolean> {
  return !editor || (await editor.save()).blocks.length === 0
}

async function clear() {
  await editor?.clear()
}

/** Adds a quote block (FR-4.5), replacing the empty first paragraph of a fresh editor. */
async function insertQuote(data: { text: string; caption: string; alignment: string }) {
  if (!editor) return
  await editor.isReady
  const replaceEmpty = editor.blocks.getBlocksCount() <= 1 && (await isEmpty())
  const index = replaceEmpty ? 0 : editor.blocks.getBlocksCount()
  editor.blocks.insert('quote', data, {}, index, true, replaceEmpty)
  // Leave a paragraph after the quote to carry on typing in.
  editor.blocks.insert('paragraph', {}, {}, index + 1, true)
}

defineExpose({ ready, save, isEmpty, clear, insertQuote })
</script>

<template>
  <div>
    <div role="group" :aria-label="label" class="post-editor form-control">
      <div ref="holder" />
    </div>
    <div v-if="uploadError" class="text-danger small mt-1" role="alert">{{ uploadError }}</div>
  </div>
</template>

<style>
/* Editor.js ships a light theme only; follow the site's colors, including dark mode. */
.post-editor {
  padding: 0.5rem 0.75rem;
  min-height: 8rem;
}

.post-editor .codex-editor__redactor {
  padding-bottom: 3rem !important;
}

.post-editor .ce-block__content,
.post-editor .ce-toolbar__content {
  max-width: none;
}

.post-editor .cdx-block,
.post-editor .cdx-input,
.post-editor .ce-code__textarea {
  color: var(--bs-body-color);
}

.post-editor .ce-code__textarea {
  background: var(--bs-tertiary-bg);
  border-color: var(--bs-border-color);
}

.post-editor .cdx-quote__text,
.post-editor .cdx-quote__caption,
.post-editor .image-tool__caption {
  background: transparent;
  border-color: var(--bs-border-color);
}

[data-bs-theme='dark'] .post-editor .ce-toolbar__plus,
[data-bs-theme='dark'] .post-editor .ce-toolbar__settings-btn,
[data-bs-theme='dark'] .post-editor .ce-block--selected .ce-block__content,
[data-bs-theme='dark'] .post-editor .cdx-list__checkbox,
[data-bs-theme='dark'] .post-editor .ce-delimiter::before {
  color: var(--bs-body-color);
}

[data-bs-theme='dark'] .post-editor .ce-toolbar__plus:hover,
[data-bs-theme='dark'] .post-editor .ce-toolbar__settings-btn:hover {
  background: var(--bs-secondary-bg);
}

/* Popovers are appended inside the editor's holder, so they inherit this scope. */
[data-bs-theme='dark'] .codex-editor .ce-popover,
[data-bs-theme='dark'] .codex-editor .ce-inline-toolbar,
[data-bs-theme='dark'] .codex-editor .ce-conversion-toolbar {
  --color-background: var(--bs-body-bg);
  --color-text-primary: var(--bs-body-color);
  --color-border: var(--bs-border-color);
  --color-background-item-hover: var(--bs-secondary-bg);
  --color-text-secondary: var(--bs-secondary-color);
  background: var(--bs-body-bg);
  color: var(--bs-body-color);
  border-color: var(--bs-border-color);
}
</style>
