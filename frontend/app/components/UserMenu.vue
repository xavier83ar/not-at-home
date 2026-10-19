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
      <UserAvatar
        :name="user.displayName"
        :src="user.avatarUrl"
        :size="22"
        aria-hidden="true"
        class="me-1 align-text-bottom"
      />
      <span class="user-name">{{ user.displayName }}</span>
    </template>
    <BDropdownText class="small text-body-secondary"
      >Signed in as @{{ user.username }}</BDropdownText
    >
    <BDropdownDivider />
    <BDropdownItem :to="profilePath(user.username)">Your profile</BDropdownItem>
    <BDropdownItem to="/settings">Settings</BDropdownItem>
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
