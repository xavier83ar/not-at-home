<script setup lang="ts">
import type { components } from '~/types/api'

type UploadResponse = components['schemas']['UploadResponse']

const props = defineProps<{
  label: string
  previewUrl?: string | null
}>()

const model = defineModel<number | null>({ required: true })

const { $api } = useNuxtApp()
const uploading = ref(false)
const error = ref<string>()
const preview = ref(props.previewUrl ?? null)

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  error.value = undefined
  uploading.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const uploaded = await $api<UploadResponse>('/api/uploads', { method: 'POST', body })
    model.value = uploaded.id
    preview.value = uploaded.url
  } catch (err) {
    error.value = uploadErrorMessage(parseProblem(err))
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="mb-3">
    <label class="form-label" for="cover-upload">{{ label }}</label>
    <img v-if="preview" :src="preview" :alt="label" class="d-block mb-2 cover-preview" />
    <input
      id="cover-upload"
      type="file"
      class="form-control"
      accept="image/jpeg,image/png,image/webp"
      :disabled="uploading"
      @change="onFileChange"
    />
    <div v-if="uploading" class="form-text">Uploading…</div>
    <div v-if="error" class="text-danger small mt-1">{{ error }}</div>
  </div>
</template>

<style scoped>
.cover-preview {
  max-width: 100%;
  max-height: 10rem;
  object-fit: cover;
  border-radius: 0.375rem;
}
</style>
