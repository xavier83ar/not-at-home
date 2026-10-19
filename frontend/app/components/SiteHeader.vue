<script setup lang="ts">
const { user } = useAuth()
const route = useRoute()

// The box shows what was searched for while the results are on screen.
const term = ref(route.path === '/search' ? parseSearchQuery(route.query).q : '')
watch(
  () => route.fullPath,
  () => {
    term.value = route.path === '/search' ? parseSearchQuery(route.query).q : ''
  },
)

// A plain GET form that works without JavaScript; once the page is hydrated this goes through the
// router instead of reloading it.
function search() {
  return navigateTo({ path: '/search', query: searchRouteQuery({ q: term.value }) })
}

// By path: the router only lights a link up for its own route, not for `/categories/hiking`.
const inCategories = computed(
  () => route.path === '/categories' || route.path.startsWith('/categories/'),
)
</script>
<template>
  <header class="border-bottom bg-body">
    <BNavbar class="container py-2" toggleable="md">
      <SiteLogo class="me-md-3" />
      <BNavbarToggle target="main-nav" />
      <BCollapse id="main-nav" is-nav>
        <BNavbarNav class="me-md-3">
          <li class="nav-item">
            <!-- Exact match: every path starts with "/", so a plain active class would light up everywhere. -->
            <NuxtLink to="/" class="nav-link" active-class="" exact-active-class="active"
              >Home</NuxtLink
            >
          </li>
          <li class="nav-item">
            <NuxtLink
              to="/categories"
              class="nav-link"
              :class="{ active: inCategories }"
              :aria-current="inCategories ? 'true' : undefined"
              >Categories</NuxtLink
            >
          </li>
        </BNavbarNav>

        <form
          class="flex-grow-1 mx-md-auto my-2 my-md-0 search-box"
          role="search"
          action="/search"
          method="get"
          @submit.prevent="search"
        >
          <label for="site-search" class="visually-hidden">Search the forum</label>
          <BFormInput
            id="site-search"
            v-model="term"
            name="q"
            type="search"
            placeholder="Search the forum"
            size="sm"
            required
            :minlength="MIN_KEYWORDS"
            :maxlength="MAX_KEYWORDS"
          />
        </form>

        <div class="d-flex align-items-center gap-2 ms-md-3">
          <!-- Guests are sent to log in and brought back; unverified users can't post (FR-1.9). -->
          <span
            v-if="user && !user.emailVerified"
            title="Verify your email address to start a topic"
          >
            <BButton
              :variant="null"
              class="btn-accent text-nowrap"
              size="sm"
              disabled
              aria-describedby="new-topic-hint"
            >
              New topic
            </BButton>
            <span id="new-topic-hint" class="visually-hidden">
              Verify your email address to start a topic
            </span>
          </span>
          <NuxtLink v-else to="/topics/new" class="btn btn-accent btn-sm text-nowrap">
            New topic
          </NuxtLink>
          <UserMenu v-if="user" />
          <template v-else>
            <NuxtLink to="/login" class="btn btn-outline-primary btn-sm text-nowrap"
              >Log in</NuxtLink
            >
            <NuxtLink to="/register" class="btn btn-primary btn-sm text-nowrap">Sign up</NuxtLink>
          </template>
          <ThemeToggle />
        </div>
      </BCollapse>
    </BNavbar>
  </header>
</template>

<style lang="scss" scoped>
@import 'bootstrap/scss/functions';
@import 'bootstrap/scss/variables';
@import 'bootstrap/scss/mixins';

@include media-breakpoint-up('md') {
  .search-box {
    max-width: 28rem;
  }
}
</style>
