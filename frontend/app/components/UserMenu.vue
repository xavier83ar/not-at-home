<script setup lang="ts">
const { user, logout } = useAuth()

async function onLogout() {
  await logout()
  await navigateTo('/')
}
</script>

<template>
  <BDropdown
    v-if="user"
    variant="outline-primary"
    size="sm"
    placement="bottom-end"
    data-testid="user-menu"
  >
    <template #button-content>
      <i class="bi bi-person-circle me-1" aria-hidden="true" />
      <span class="user-name">{{ user.displayName }}</span>
    </template>
    <BDropdownText class="small text-body-secondary"
      >Signed in as @{{ user.username }}</BDropdownText
    >
    <template v-if="user.role === 'ADMIN'">
      <BDropdownDivider />
      <BDropdownItem to="/admin/categories">Manage categories</BDropdownItem>
    </template>
    <BDropdownDivider />
    <BDropdownItem @click="onLogout">Log out</BDropdownItem>
  </BDropdown>
</template>

<style scoped>
.user-name {
  display: inline-block;
  max-width: 10rem;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}
</style>
