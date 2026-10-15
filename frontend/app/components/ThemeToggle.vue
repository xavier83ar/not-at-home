<script setup lang="ts">
const colorMode = useColorMode()

const options = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
] as const
</script>

<template>
  <BDropdown variant="link" size="sm" toggle-class="text-body" no-caret placement="bottom-end">
    <template #button-content>
      <span class="visually-hidden">Theme: {{ colorMode.preference }}</span>
      <ClientOnly>
        <span aria-hidden="true">{{ colorMode.value === 'dark' ? '☾' : '☀' }}</span>
        <template #fallback><span aria-hidden="true">☀</span></template>
      </ClientOnly>
    </template>
    <BDropdownItem
      v-for="option in options"
      :key="option.value"
      :active="colorMode.preference === option.value"
      @click="colorMode.preference = option.value"
    >
      {{ option.label }}
    </BDropdownItem>
  </BDropdown>
</template>
