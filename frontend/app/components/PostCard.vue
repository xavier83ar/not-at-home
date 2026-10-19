<script setup lang="ts">
import type { components } from '~/types/api'
import PostEditor from '~/components/PostEditor.vue'

type PostResponse = components['schemas']['PostResponse']
type DeletePostResponse = components['schemas']['DeletePostResponse']

/**
 * One post in a topic (FR-4.2). A removed post shows a placeholder (FR-5.6); admins also get its
 * content, labelled as removed (FR-5.8). The author can edit it in place (FR-5.1) or delete it
 * (FR-5.2); anyone who may reply can quote it (FR-4.5) and anyone can copy its link (FR-4.6).
 */
const props = defineProps<{ post: PostResponse; canReply?: boolean }>()
const emit = defineEmits<{
  quote: [post: PostResponse]
  changed: []
  deleted: [result: DeletePostResponse]
}>()

const { $api } = useNuxtApp()
const { user } = useAuth()

const isAuthor = computed(() => user.value?.id === props.post.author.id)
const editing = ref(false)
const busy = ref(false)
const error = ref<string>()
const copied = ref(false)
const editor = ref<InstanceType<typeof PostEditor>>()

async function copyLink() {
  await navigator.clipboard.writeText(`${window.location.origin}${postPath(props.post.id)}`)
  copied.value = true
  setTimeout(() => (copied.value = false), 2000)
}

async function saveEdit() {
  if (!editor.value || busy.value) return
  error.value = undefined
  if (await editor.value.isEmpty()) {
    error.value = 'A post can’t be empty. Delete it instead.'
    return
  }
  busy.value = true
  try {
    await $api(`/api/posts/${props.post.id}`, {
      method: 'PUT',
      body: { content: await editor.value.save() },
    })
    editing.value = false
    emit('changed')
  } catch (err) {
    error.value = postErrorMessage(parseProblem(err)) ?? 'Your changes could not be saved.'
  } finally {
    busy.value = false
  }
}

async function remove() {
  if (!window.confirm('Delete this post? This can’t be undone.')) return
  busy.value = true
  error.value = undefined
  try {
    emit(
      'deleted',
      await $api<DeletePostResponse>(`/api/posts/${props.post.id}`, { method: 'DELETE' }),
    )
  } catch (err) {
    error.value = postErrorMessage(parseProblem(err)) ?? 'The post could not be deleted.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <article :id="`post-${post.id}`" class="card mb-3 post" :class="{ 'post-removed': post.removed }">
    <div class="card-header d-flex align-items-center gap-2 bg-transparent">
      <UserAvatar :name="post.author.displayName" :src="post.author.avatarUrl" :size="32" />
      <div class="d-flex flex-column flex-sm-row align-items-sm-center column-gap-2 me-auto">
        <AuthorName :author="post.author" class="fw-semibold" />
        <span v-if="!post.author.deleted" class="small text-body-secondary"
          >@{{ post.author.username }}</span
        >
      </div>
      <span v-if="post.author.role === 'ADMIN'" class="badge text-bg-accent">Admin</span>
      <span v-if="post.opening" class="badge text-bg-secondary">Original post</span>
      <NuxtLink
        :to="postPath(post.id)"
        class="small text-body-secondary text-decoration-none"
        title="Link to this post"
      >
        <TimeAgo :date="post.createdAt" />
      </NuxtLink>
    </div>
    <div class="card-body">
      <p v-if="post.removed && !post.content" class="text-body-secondary fst-italic mb-0">
        This post was removed.
      </p>
      <template v-else-if="editing">
        <ClientOnly>
          <PostEditor ref="editor" label="Edit your post" :initial="post.content" />
        </ClientOnly>
        <BAlert v-if="error" :model-value="true" variant="danger" class="mt-3 mb-0">
          {{ error }}
        </BAlert>
        <div class="mt-3 d-flex gap-2">
          <BButton variant="primary" size="sm" :disabled="busy" @click="saveEdit">
            {{ busy ? 'Saving…' : 'Save changes' }}
          </BButton>
          <BButton variant="outline-secondary" size="sm" :disabled="busy" @click="editing = false">
            Cancel
          </BButton>
        </div>
      </template>
      <template v-else>
        <p v-if="post.removed" class="small text-danger-emphasis mb-3" data-testid="removed-label">
          <i class="bi bi-eye-slash me-1" aria-hidden="true" />
          Removed post. Only admins can see this content.
        </p>
        <EditorContent :document="post.content" />
        <p v-if="post.editedAt" class="small text-body-secondary mt-3 mb-0" data-testid="edited">
          Edited <TimeAgo :date="post.editedAt" />
        </p>
      </template>
    </div>
    <div v-if="!post.removed && !editing" class="card-footer bg-transparent">
      <div class="d-flex flex-wrap align-items-center column-gap-3 small">
        <button
          v-if="canReply"
          type="button"
          class="btn btn-link btn-sm p-0"
          @click="emit('quote', post)"
        >
          <i class="bi bi-quote me-1" aria-hidden="true" />Quote
        </button>
        <button type="button" class="btn btn-link btn-sm p-0" @click="copyLink">
          <i class="bi bi-link-45deg me-1" aria-hidden="true" />{{
            copied ? 'Link copied' : 'Copy link'
          }}
        </button>
        <template v-if="isAuthor">
          <button type="button" class="btn btn-link btn-sm p-0" @click="editing = true">
            <i class="bi bi-pencil me-1" aria-hidden="true" />Edit
          </button>
          <button
            type="button"
            class="btn btn-link btn-sm p-0 text-danger"
            :disabled="busy"
            @click="remove"
          >
            <i class="bi bi-trash me-1" aria-hidden="true" />Delete
          </button>
        </template>
      </div>
      <BAlert v-if="error" :model-value="true" variant="danger" class="mt-2 mb-0 py-1 small">
        {{ error }}
      </BAlert>
    </div>
  </article>
</template>

<style scoped>
.post-removed {
  border-style: dashed;
}
</style>
