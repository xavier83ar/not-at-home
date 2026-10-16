<script setup lang="ts">
import type { components } from '~/types/api'
import PostEditor from '~/components/PostEditor.vue'

type PostResponse = components['schemas']['PostResponse']

/** The reply form at the bottom of a topic (FR-4.1). Only shown to people who may reply. */
const props = defineProps<{ topicId: number }>()
const emit = defineEmits<{ posted: [post: PostResponse] }>()

const { $api } = useNuxtApp()
const { user } = useAuth()
const editor = ref<InstanceType<typeof PostEditor>>()
const container = ref<HTMLElement>()
const submitting = ref(false)
const error = ref<string>()

async function submit() {
  if (!editor.value || submitting.value) return
  error.value = undefined
  if (await editor.value.isEmpty()) {
    error.value = 'Write something before posting.'
    return
  }
  submitting.value = true
  try {
    const post = await $api<PostResponse>(`/api/topics/${props.topicId}/posts`, {
      method: 'POST',
      body: { content: await editor.value.save() },
    })
    await editor.value.clear()
    emit('posted', post)
  } catch (err) {
    error.value = postErrorMessage(parseProblem(err)) ?? 'Your reply could not be posted.'
  } finally {
    submitting.value = false
  }
}

/** Called from the "Quote" action of a post (FR-4.5). */
async function quote(post: Parameters<typeof quoteBlockData>[0]) {
  await editor.value?.insertQuote(quoteBlockData(post))
  container.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

defineExpose({ quote })
</script>

<template>
  <section v-if="user" ref="container" aria-labelledby="reply-heading" class="mt-4">
    <h2 id="reply-heading" class="h5">Reply</h2>
    <BAlert v-if="!user.emailVerified" :model-value="true" variant="warning">
      Verify your email address to reply. Check your inbox for the link, or use the banner at the
      top of the page to send it again.
    </BAlert>
    <form v-else @submit.prevent="submit">
      <ClientOnly>
        <PostEditor ref="editor" label="Your reply" placeholder="Write a reply…" />
        <template #fallback>
          <p class="text-body-secondary">Loading the editor… It needs JavaScript.</p>
        </template>
      </ClientOnly>
      <BAlert v-if="error" :model-value="true" variant="danger" class="mt-3 mb-0">
        {{ error }}
      </BAlert>
      <BButton type="submit" variant="primary" class="mt-3" :disabled="submitting">
        {{ submitting ? 'Posting…' : 'Post reply' }}
      </BButton>
    </form>
  </section>
</template>
