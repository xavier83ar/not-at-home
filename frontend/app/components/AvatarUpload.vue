<script setup lang="ts">
import type { components } from '~/types/api'

type UploadResponse = components['schemas']['UploadResponse']

/**
 * The avatar picker of the settings page (FR-8.2). A new picture is uploaded and applied at once,
 * since a half-saved profile is no use to anyone, and it is shown to the user in the top bar too.
 * Without one, the user gets their initials.
 */
const props = defineProps<{ name: string; src?: string | null }>()

const { $api } = useNuxtApp()
const { fetchUser } = useAuth()
const current = ref(props.src ?? null)
const busy = ref(false)
const error = ref<string>()

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  error.value = undefined
  busy.value = true
  try {
    let uploaded: UploadResponse
    try {
      const body = new FormData()
      body.append('file', file)
      uploaded = await $api<UploadResponse>('/api/uploads', { method: 'POST', body })
    } catch (err) {
      error.value = uploadErrorMessage(parseProblem(err))
      return
    }
    try {
      await $api('/api/me/avatar', { method: 'PUT', body: { uploadId: uploaded.id } })
    } catch (err) {
      error.value = profileErrorMessage(parseProblem(err)) ?? 'Your avatar could not be saved.'
      return
    }
    current.value = uploaded.url
    await fetchUser()
  } finally {
    busy.value = false
    input.value = ''
  }
}

async function remove() {
  error.value = undefined
  busy.value = true
  try {
    await $api('/api/me/avatar', { method: 'DELETE' })
    current.value = null
    await fetchUser()
  } catch (err) {
    error.value = profileErrorMessage(parseProblem(err)) ?? 'Your avatar could not be removed.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="d-flex flex-column flex-sm-row align-items-sm-center gap-3 mb-4">
    <UserAvatar :name="name" :src="current" :size="96" />
    <div>
      <label class="form-label" for="avatar-upload">Avatar</label>
      <input
        id="avatar-upload"
        type="file"
        class="form-control"
        accept="image/jpeg,image/png,image/webp"
        aria-describedby="avatar-hint"
        :disabled="busy"
        @change="onFileChange"
      />
      <div id="avatar-hint" class="form-text">
        JPEG, PNG or WebP, up to 5 MB. Without one, your initials are shown.
      </div>
      <BButton
        v-if="current"
        variant="link"
        size="sm"
        class="p-0 mt-1"
        :disabled="busy"
        @click="remove"
      >
        Remove avatar
      </BButton>
      <div v-if="busy" class="form-text">Saving…</div>
      <div v-if="error" class="text-danger small mt-1" role="alert">{{ error }}</div>
    </div>
  </div>
</template>
