<template>
  <div class="d-flex flex-column flex-grow-1">
    <SiteHeader />
    <VerifyEmailBanner />
    <main class="container py-4 flex-grow-1">
      <slot />
    </main>
    <SiteFooter />
  </div>
</template>
