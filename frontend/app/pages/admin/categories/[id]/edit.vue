<script setup lang="ts">
import type { components } from '~/types/api'

type CategoryResponse = components['schemas']['CategoryResponse']

definePageMeta({ middleware: 'admin' })

const route = useRoute()
const id = Number(route.params.id)

const { $api } = useNuxtApp()
// Reuses the public list rather than a dedicated admin lookup endpoint: admins see the same
// active categories, and this is the only place that needs one by id.
const { data: categories } = await useAsyncData('admin-categories-for-edit', () =>
  $api<CategoryResponse[]>('/api/categories'),
)
const category = computed(() => categories.value?.find((c) => c.id === id))

useSeoMeta({
  title: category.value ? `Edit ${category.value.name}` : 'Edit category',
  robots: 'noindex',
})

const submitting = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit(payload: {
  name: string
  description: string
  coverUploadId: number | null
}) {
  submitting.value = true
  message.value = undefined
  fieldErrors.value = {}
  try {
    await $api(`/api/admin/categories/${id}`, { method: 'PUT', body: payload })
  } catch (err) {
    const problem = parseProblem(err)
    fieldErrors.value = problem.fieldErrors
    message.value = categoryFormErrorMessage(problem)
    submitting.value = false
    return
  }
  await navigateTo('/admin/categories')
}
</script>

<template>
  <section>
    <h1 class="h3 mb-4">Edit category</h1>

    <BAlert v-if="!category" :model-value="true" variant="warning">
      This category no longer exists.
    </BAlert>
    <template v-else>
      <BAlert :model-value="!!message" variant="danger">{{ message }}</BAlert>
      <CategoryForm
        :initial-name="category.name"
        :initial-description="category.description"
        :initial-cover-upload-id="category.coverUploadId"
        :initial-cover-image-url="category.coverImageUrl"
        :submitting="submitting"
        submit-label="Save changes"
        :field-errors="fieldErrors"
        @submit="onSubmit"
      />
    </template>
  </section>
</template>
