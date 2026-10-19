<script setup lang="ts">
import type { components } from '~/types/api'
import PostEditor from '~/components/PostEditor.vue'

type UserProfile = components['schemas']['UserProfileResponse']

/**
 * The profile part of the settings page (FR-8.2): avatar, display name and the profile text,
 * written with Editor.js like a post. The form starts from the public profile.
 */
const props = defineProps<{ profile: UserProfile }>()

const { $api } = useNuxtApp()
const { fetchUser } = useAuth()
const editor = ref<InstanceType<typeof PostEditor>>()

// See login.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const displayName = ref(props.profile.displayName)
const submitting = ref(false)
const saved = ref(false)
const message = ref<string>()
const fieldErrors = ref<Record<string, string[]>>({})

async function onSubmit() {
  if (!editor.value?.ready || submitting.value) return
  submitting.value = true
  saved.value = false
  message.value = undefined
  fieldErrors.value = {}
  try {
    await $api('/api/me/profile', {
      method: 'PUT',
      body: {
        displayName: displayName.value,
        // Nothing written clears the text.
        profileContent: (await editor.value.isEmpty()) ? null : await editor.value.save(),
      },
    })
    await fetchUser()
    saved.value = true
  } catch (err) {
    const problem = parseProblem(err)
    fieldErrors.value = problem.fieldErrors
    message.value = profileErrorMessage(problem)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section aria-labelledby="profile-heading">
    <h2 id="profile-heading" class="h4 mb-3">Profile</h2>

    <AvatarUpload :name="profile.displayName" :src="profile.avatarUrl" />

    <BAlert :model-value="!!message" variant="danger" data-testid="profile-error">
      {{ message }}
    </BAlert>
    <BAlert :model-value="saved" variant="success" data-testid="profile-saved">
      Your profile has been saved.
    </BAlert>

    <BForm method="post" novalidate @submit.prevent="onSubmit">
      <FormTextField
        id="displayName"
        v-model="displayName"
        label="Display name"
        autocomplete="nickname"
        :hint="`The name people see on your posts. Your username, @${profile.username}, can't be changed.`"
        :errors="fieldErrors.displayName"
      />
      <div class="mb-3">
        <div class="form-label">About you</div>
        <ClientOnly>
          <PostEditor
            ref="editor"
            label="About you"
            placeholder="Tell people about yourself…"
            :initial="profile.profileContent"
          />
          <template #fallback>
            <p class="text-body-secondary">Loading the editor… It needs JavaScript.</p>
          </template>
        </ClientOnly>
        <div class="form-text">Shown on your public profile. Leave it empty to show nothing.</div>
      </div>
      <BButton
        type="submit"
        variant="primary"
        :disabled="submitting || !hydrated || !editor?.ready"
      >
        {{ submitting ? 'Saving…' : 'Save profile' }}
      </BButton>
    </BForm>
  </section>
</template>
