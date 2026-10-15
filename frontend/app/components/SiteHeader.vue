<script setup lang="ts"></script>
<template>
  <header class="border-bottom bg-body">
    <BNavbar class="container py-2" toggleable="md">
      <SiteLogo class="me-md-3" />
      <BNavbarToggle target="main-nav" />
      <BCollapse id="main-nav" is-nav>
        <BNavbarNav class="me-md-3">
          <li class="nav-item">
            <NuxtLink to="/" class="nav-link" active-class="active">Home</NuxtLink>
          </li>
          <li class="nav-item">
            <NuxtLink to="/categories" class="nav-link" active-class="active">Categories</NuxtLink>
          </li>
        </BNavbarNav>

        <!-- Search box placeholder; wired up in M5/M6. -->
        <form class="flex-grow-1 mx-md-auto my-2 my-md-0 search-box" role="search" @submit.prevent>
          <label for="site-search" class="visually-hidden">Search the forum</label>
          <BFormInput
            id="site-search"
            type="search"
            placeholder="Search the forum"
            size="sm"
            disabled
          />
        </form>

        <!-- New topic and user menu placeholders; wired up in M1/M4. -->
        <div class="d-flex align-items-center gap-2 ms-md-3">
          <BButton :variant="null" class="btn-accent" size="sm" disabled>New topic</BButton>
          <BButton variant="outline-primary" size="sm" disabled>Log in</BButton>
          <BButton variant="primary" size="sm" disabled>Sign up</BButton>
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
