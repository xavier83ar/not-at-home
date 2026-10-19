<script setup lang="ts">
import type { components } from '~/types/api'

type UserProfile = components['schemas']['UserProfileResponse']

const route = useRoute()
const { $api } = useNuxtApp()
const { user } = useAuth()

const username = String(route.params.username)
const { data: profile, error } = await useAsyncData(`user-${username.toLowerCase()}`, () =>
  $api<UserProfile>(`/api/users/${encodeURIComponent(username)}`),
)
if (error.value) {
  const status = parseProblem(error.value).status
  throw createError({
    statusCode: status === 404 ? 404 : 500,
    statusMessage: status === 404 ? 'User not found' : 'The profile could not be loaded',
    fatal: true,
  })
}

// Usernames are matched ignoring case; the canonical address uses the real one (permanent).
if (profile.value && username !== profile.value.username) {
  await navigateTo(profilePath(profile.value.username), { redirectCode: 301, replace: true })
}

const isOwn = computed(() => !!user.value && user.value.id === profile.value?.id)

useSeo(() => ({
  title: profile.value ? `${profile.value.displayName} (@${profile.value.username})` : 'Profile',
  description:
    (profile.value && excerpt(profile.value.profileContent)) ||
    (profile.value
      ? `${profile.value.displayName} (@${profile.value.username}) on Not at Home.`
      : undefined),
  path: profilePath(profile.value?.username ?? username),
  image: profile.value?.avatarUrl,
  type: 'website',
  breadcrumbs: [
    {
      name: profile.value?.displayName ?? username,
      path: profilePath(profile.value?.username ?? username),
    },
  ],
}))
</script>

<template>
  <section v-if="profile">
    <BreadcrumbNav :crumbs="[{ label: profile.displayName }]" />

    <header class="card mb-4">
      <div class="card-body d-flex flex-column flex-sm-row align-items-sm-center gap-3">
        <UserAvatar :name="profile.displayName" :src="profile.avatarUrl" :size="96" />
        <div class="flex-grow-1 min-w-0">
          <h1 class="h3 mb-0 d-flex flex-wrap align-items-center column-gap-2">
            <span class="name">{{ profile.displayName }}</span>
            <span v-if="profile.role === 'ADMIN'" class="badge text-bg-accent fs-6">Admin</span>
          </h1>
          <p class="text-body-secondary mb-2">@{{ profile.username }}</p>
          <p class="small text-body-secondary mb-0">
            Joined <TimeAgo :date="profile.joinedAt" /> · {{ profile.postCount }}
            {{ profile.postCount === 1 ? 'post' : 'posts' }}
          </p>
        </div>
        <NuxtLink v-if="isOwn" to="/settings" class="btn btn-outline-primary btn-sm">
          Edit profile
        </NuxtLink>
      </div>
    </header>

    <section v-if="profile.profileContent" aria-labelledby="about-heading" class="mb-4">
      <h2 id="about-heading" class="h5">About</h2>
      <EditorContent :document="profile.profileContent" />
    </section>
    <p v-else-if="isOwn" class="text-body-secondary mb-4">
      You haven't written anything about yourself yet.
      <NuxtLink to="/settings">Add something to your profile.</NuxtLink>
    </p>

    <section aria-labelledby="topics-heading" class="mb-4">
      <h2 id="topics-heading" class="h5">Recent topics</h2>
      <p v-if="profile.recentTopics.length === 0" class="text-body-secondary">
        No topics started yet.
      </p>
      <ul v-else class="list-group">
        <TopicRow
          v-for="topic in profile.recentTopics"
          :key="topic.id"
          :topic="topic"
          show-category
        />
      </ul>
    </section>

    <section aria-labelledby="posts-heading" class="mb-4">
      <h2 id="posts-heading" class="h5">Recent posts</h2>
      <p v-if="profile.recentPosts.length === 0" class="text-body-secondary">No posts yet.</p>
      <ul v-else class="list-group">
        <ProfilePostItem v-for="post in profile.recentPosts" :key="post.postId" :post="post" />
      </ul>
    </section>
  </section>
</template>

<style scoped>
.min-w-0 {
  min-width: 0;
}

.name {
  overflow-wrap: anywhere;
}
</style>
