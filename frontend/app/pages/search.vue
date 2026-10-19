<script setup lang="ts">
import type { components } from '~/types/api'

type SearchPage = components['schemas']['PageResponseSearchResult']
type CategoryResponse = components['schemas']['CategoryResponse']

const route = useRoute()
const { $api } = useNuxtApp()

const filters = computed(() => parseSearchQuery(route.query))
const page = computed(() => parsePage(route.query.page))
const apiQuery = computed(() => ({
  ...searchRouteQuery(filters.value),
  ...(page.value > 1 ? { page: page.value } : {}),
}))

// Result pages are not worth indexing, and every query is a new page (section 4.3).
useSeoMeta({
  title: () => (filters.value.q ? `Search: ${filters.value.q}` : 'Search'),
  robots: 'noindex',
})

const { data: categories } = await useAsyncData('search-categories', () =>
  $api<CategoryResponse[]>('/api/categories'),
)

// Without keywords there is nothing to ask the backend (FR-6.1).
const { data: results, error } = await useAsyncData(
  () => `search-${JSON.stringify(apiQuery.value)}`,
  () =>
    filters.value.q
      ? $api<SearchPage>('/api/search', { query: apiQuery.value })
      : Promise.resolve(null),
  { watch: [apiQuery] },
)
const problem = computed(() => (error.value ? parseProblem(error.value) : undefined))
// A page past the end is a 404 for search engines too (the backend answers it that way).
if (problem.value?.status === 404) {
  throw createError({ statusCode: 404, statusMessage: 'Page not found', fatal: true })
}
const fieldErrors = computed(() => (problem.value?.status === 400 ? problem.value.fieldErrors : {}))
const loadFailed = computed(() => !!problem.value && problem.value.status !== 400)

// The form has its own copy of the filters, so typing doesn't search; it follows the address when
// that changes (back button, the box in the top bar).
const form = reactive<SearchFilters>({ ...filters.value })
watch(filters, (value) => Object.assign(form, value))

const sortOptions = [
  { value: 'relevance', text: 'Most relevant' },
  { value: 'date', text: 'Newest first' },
]
const categoryOptions = computed(() => [
  { value: '', text: 'Any category' },
  ...(categories.value ?? []).map((category) => ({ value: category.slug, text: category.name })),
])

function fieldMessage(field: string, label: string): string | undefined {
  const message = fieldErrors.value[field]?.[0]
  return message ? `${label} ${message}` : undefined
}

// Without JavaScript the form is an ordinary GET to this page; with it, the address is built from
// the filters that are set, so it stays short.
function onSubmit() {
  return navigateTo({ path: '/search', query: searchRouteQuery(form) })
}
</script>

<template>
  <section>
    <BreadcrumbNav :crumbs="[{ label: 'Search' }]" />
    <h1 class="h3 mb-4">Search</h1>

    <form
      action="/search"
      method="get"
      role="search"
      aria-label="Search with filters"
      class="card card-body mb-4"
      @submit.prevent="onSubmit"
    >
      <div class="row g-3">
        <div class="col-12">
          <label for="search-keywords" class="form-label">Keywords</label>
          <BFormInput
            id="search-keywords"
            v-model="form.q"
            name="q"
            type="search"
            required
            :minlength="MIN_KEYWORDS"
            :maxlength="MAX_KEYWORDS"
            :state="fieldErrors.q ? false : null"
            aria-describedby="search-keywords-hint"
          />
          <BFormInvalidFeedback :state="fieldErrors.q ? false : null">
            {{ fieldMessage('q', 'Keywords') }}
          </BFormInvalidFeedback>
          <BFormText id="search-keywords-hint">
            Use quotes for an exact phrase and a minus sign to leave a word out.
          </BFormText>
        </div>
        <div class="col-12 col-md-6">
          <label for="search-category" class="form-label">Category</label>
          <BFormSelect
            id="search-category"
            v-model="form.category"
            name="category"
            :options="categoryOptions"
          />
        </div>
        <div class="col-12 col-md-6">
          <label for="search-author" class="form-label">Author</label>
          <BFormInput
            id="search-author"
            v-model="form.author"
            name="author"
            autocomplete="off"
            placeholder="Username"
          />
        </div>
        <div class="col-12 col-md-4">
          <label for="search-from" class="form-label">From</label>
          <BFormInput
            id="search-from"
            v-model="form.from"
            name="from"
            type="date"
            :state="fieldErrors.from ? false : null"
          />
          <BFormInvalidFeedback :state="fieldErrors.from ? false : null">
            {{ fieldMessage('from', 'Start date') }}
          </BFormInvalidFeedback>
        </div>
        <div class="col-12 col-md-4">
          <label for="search-to" class="form-label">To</label>
          <BFormInput
            id="search-to"
            v-model="form.to"
            name="to"
            type="date"
            :state="fieldErrors.to ? false : null"
          />
          <BFormInvalidFeedback :state="fieldErrors.to ? false : null">
            {{ fieldMessage('to', 'End date') }}
          </BFormInvalidFeedback>
        </div>
        <div class="col-12 col-md-4">
          <label for="search-sort" class="form-label">Sort by</label>
          <BFormSelect id="search-sort" v-model="form.sort" name="sort" :options="sortOptions" />
        </div>
        <div class="col-12">
          <button type="submit" class="btn btn-primary">Search</button>
        </div>
      </div>
    </form>

    <BAlert v-if="loadFailed" :model-value="true" variant="warning">
      The search could not be completed. Please try again later.
    </BAlert>

    <p v-else-if="!filters.q" class="text-body-secondary">
      Search the topics and replies of every category.
    </p>

    <p v-else-if="results?.items.length === 0" class="text-body-secondary">
      Nothing found for “{{ filters.q }}”. Try other words, or fewer filters.
    </p>

    <template v-else-if="results">
      <p class="text-body-secondary" role="status">
        {{ results.totalItems }} {{ results.totalItems === 1 ? 'result' : 'results' }} for “{{
          filters.q
        }}”
      </p>
      <ul class="list-group mb-4">
        <SearchResultItem v-for="item in results.items" :key="item.postId" :result="item" />
      </ul>
      <PaginationNav :page="results.page" :total-pages="results.totalPages" />
    </template>
  </section>
</template>
