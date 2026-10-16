<script setup lang="ts">
import type { components } from '~/types/api'
import PostEditor from '~/components/PostEditor.vue'

type CategoryResponse = components['schemas']['CategoryResponse']
type TopicSummary = components['schemas']['TopicSummary']

definePageMeta({ middleware: 'auth' })
useSeoMeta({ title: 'New topic', robots: 'noindex' })

const route = useRoute()
const { $api } = useNuxtApp()
const { user } = useAuth()

const { data: categories, error: loadError } = await useAsyncData('new-topic-categories', () =>
  $api<CategoryResponse[]>('/api/categories'),
)

const preselected = firstQueryValue(route.query.category)
const categoryId = ref<number | null>(
  categories.value?.find((category) => category.slug === preselected)?.id ?? null,
)
const title = ref('')
const editor = ref<InstanceType<typeof PostEditor>>()
const submitting = ref(false)
const error = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

const canPost = computed(() => user.value?.emailVerified === true)

async function submit() {
  if (!editor.value || submitting.value) return
  error.value = undefined
  fieldErrors.value = {}
  if (await editor.value.isEmpty()) {
    fieldErrors.value = { content: ['can’t be empty'] }
    error.value = 'Write the first post of the topic before publishing it.'
    return
  }
  submitting.value = true
  try {
    const topic = await $api<TopicSummary>('/api/topics', {
      method: 'POST',
      body: {
        categoryId: categoryId.value,
        title: title.value,
        content: await editor.value.save(),
      },
    })
    await navigateTo(topicPath(topic.id, topic.slug))
  } catch (err) {
    const problem = parseProblem(err)
    fieldErrors.value = problem.fieldErrors
    error.value = postErrorMessage(problem)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="mx-auto new-topic">
    <BreadcrumbNav :crumbs="[{ label: 'New topic' }]" />
    <h1 class="h3 mb-4">New topic</h1>

    <BAlert v-if="loadError" :model-value="true" variant="warning">
      The categories could not be loaded. Please try again later.
    </BAlert>
    <BAlert v-else-if="!canPost" :model-value="true" variant="warning" data-testid="verify-notice">
      Verify your email address to start a topic. Check your inbox for the link, or use the banner
      at the top of the page to send it again.
    </BAlert>

    <form v-else novalidate @submit.prevent="submit">
      <BFormGroup label="Category" label-for="topic-category" class="mb-3">
        <BFormSelect
          id="topic-category"
          v-model="categoryId"
          :state="fieldErrors.categoryId ? false : null"
          required
        >
          <option :value="null" disabled>Choose a category</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">
            {{ category.name }}
          </option>
        </BFormSelect>
        <BFormInvalidFeedback :state="fieldErrors.categoryId ? false : null">
          Category {{ fieldErrors.categoryId?.[0] }}
        </BFormInvalidFeedback>
      </BFormGroup>

      <BFormGroup label="Title" label-for="topic-title" class="mb-3">
        <BFormInput
          id="topic-title"
          v-model="title"
          maxlength="128"
          autocomplete="off"
          :state="fieldErrors.title ? false : null"
          required
        />
        <BFormInvalidFeedback :state="fieldErrors.title ? false : null">
          Title {{ fieldErrors.title?.[0] }}
        </BFormInvalidFeedback>
        <BFormText>{{ title.length }}/128</BFormText>
      </BFormGroup>

      <div class="mb-3">
        <div class="form-label">First post</div>
        <ClientOnly>
          <PostEditor
            ref="editor"
            label="First post"
            placeholder="Tell the story, ask the question…"
          />
          <template #fallback>
            <p class="text-body-secondary">Loading the editor… It needs JavaScript.</p>
          </template>
        </ClientOnly>
      </div>

      <BAlert v-if="error" :model-value="true" variant="danger">{{ error }}</BAlert>

      <div class="d-flex gap-2">
        <BButton type="submit" variant="primary" :disabled="submitting">
          {{ submitting ? 'Publishing…' : 'Publish topic' }}
        </BButton>
        <NuxtLink to="/categories" class="btn btn-outline-secondary">Cancel</NuxtLink>
      </div>
    </form>
  </section>
</template>

<style scoped>
.new-topic {
  max-width: 48rem;
}
</style>
