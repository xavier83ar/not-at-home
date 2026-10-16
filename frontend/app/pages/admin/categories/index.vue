<script setup lang="ts">
import type { components } from '~/types/api'

type CategoryResponse = components['schemas']['CategoryResponse']

definePageMeta({ middleware: 'admin' })
useSeoMeta({ title: 'Manage categories', robots: 'noindex' })

const { $api } = useNuxtApp()
const {
  data: categories,
  error,
  refresh,
} = await useAsyncData('admin-categories', () => $api<CategoryResponse[]>('/api/categories'))

const message = ref<string>()
const busyId = ref<number>()

async function move(category: CategoryResponse, direction: 'up' | 'down') {
  if (!categories.value) return
  const index = categories.value.indexOf(category)
  const reordered = moveItem(categories.value, index, direction)
  if (reordered === categories.value) return

  busyId.value = category.id
  message.value = undefined
  try {
    await $api('/api/admin/categories/order', {
      method: 'PUT',
      body: { orderedIds: reordered.map((c) => c.id) },
    })
    await refresh()
  } catch (err) {
    message.value = categoryActionErrorMessage(parseProblem(err))
  } finally {
    busyId.value = undefined
  }
}

async function remove(category: CategoryResponse) {
  if (!window.confirm(`Delete "${category.name}"? This can't be undone here.`)) return

  busyId.value = category.id
  message.value = undefined
  try {
    await $api(`/api/admin/categories/${category.id}`, { method: 'DELETE' })
    await refresh()
  } catch (err) {
    message.value = categoryActionErrorMessage(parseProblem(err))
  } finally {
    busyId.value = undefined
  }
}
</script>

<template>
  <section>
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h1 class="h3 mb-0">Manage categories</h1>
      <NuxtLink to="/admin/categories/new" class="btn btn-primary">New category</NuxtLink>
    </div>

    <BAlert v-if="error" :model-value="true" variant="warning">
      Categories could not be loaded. Please try again later.
    </BAlert>
    <BAlert :model-value="!!message" variant="danger">{{ message }}</BAlert>

    <p v-if="categories?.length === 0" class="text-body-secondary">No categories yet.</p>

    <table v-else-if="categories" class="table align-middle">
      <thead>
        <tr>
          <th scope="col" class="visually-hidden">Order</th>
          <th scope="col">Cover</th>
          <th scope="col">Name</th>
          <th scope="col">Slug</th>
          <th scope="col" class="text-end">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(category, index) in categories" :key="category.id">
          <td class="text-nowrap">
            <BButton
              size="sm"
              variant="outline-secondary"
              :disabled="index === 0 || busyId === category.id"
              :aria-label="`Move ${category.name} up`"
              @click="move(category, 'up')"
            >
              <i class="bi bi-arrow-up" aria-hidden="true" />
            </BButton>
            <BButton
              size="sm"
              variant="outline-secondary"
              :disabled="index === categories.length - 1 || busyId === category.id"
              :aria-label="`Move ${category.name} down`"
              @click="move(category, 'down')"
            >
              <i class="bi bi-arrow-down" aria-hidden="true" />
            </BButton>
          </td>
          <td>
            <img
              v-if="category.coverImageUrl"
              :src="category.coverImageUrl"
              :alt="category.name"
              class="cover-thumb"
            />
          </td>
          <td>{{ category.name }}</td>
          <td class="text-body-secondary">{{ category.slug }}</td>
          <td class="text-end text-nowrap">
            <NuxtLink
              :to="`/admin/categories/${category.id}/edit`"
              class="btn btn-sm btn-outline-secondary me-2"
            >
              Edit
            </NuxtLink>
            <BButton
              size="sm"
              variant="outline-danger"
              :disabled="busyId === category.id"
              @click="remove(category)"
            >
              Delete
            </BButton>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.cover-thumb {
  width: 3rem;
  height: 3rem;
  object-fit: cover;
  border-radius: 0.375rem;
}
</style>
