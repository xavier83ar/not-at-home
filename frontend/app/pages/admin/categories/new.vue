<script setup lang="ts">
definePageMeta({ middleware: 'admin' })
useSeoMeta({ title: 'New category', robots: 'noindex' })

const { $api } = useNuxtApp()
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
    await $api('/api/admin/categories', { method: 'POST', body: payload })
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
    <h1 class="h3 mb-4">New category</h1>
    <BAlert :model-value="!!message" variant="danger">{{ message }}</BAlert>
    <CategoryForm
      :submitting="submitting"
      submit-label="Create category"
      :field-errors="fieldErrors"
      @submit="onSubmit"
    />
  </section>
</template>
