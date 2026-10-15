<script setup lang="ts">
/**
 * A labelled input that shows the backend's validation messages. Messages are written to read
 * after the field name ("is already taken"), so the label is put in front of them.
 */
const props = defineProps<{
  id: string
  label: string
  type?: 'text' | 'email' | 'password'
  autocomplete?: string
  hint?: string
  autofocus?: boolean
  errors?: string[]
}>()

const model = defineModel<string>({ required: true })

const error = computed(() =>
  props.errors?.length ? `${props.label} ${props.errors[0]}` : undefined,
)
</script>

<template>
  <BFormGroup :label="label" :label-for="id" class="mb-3">
    <BFormInput
      :id="id"
      v-model="model"
      :name="id"
      :type="type ?? 'text'"
      :autocomplete="autocomplete"
      :autofocus="autofocus"
      :aria-describedby="hint ? `${id}-hint` : undefined"
      :state="error ? false : null"
      required
    />
    <BFormInvalidFeedback :state="error ? false : null">{{ error }}</BFormInvalidFeedback>
    <BFormText v-if="hint" :id="`${id}-hint`">{{ hint }}</BFormText>
  </BFormGroup>
</template>
