<script setup lang="ts">
const props = defineProps<{
  initialName?: string
  initialDescription?: string
  initialCoverUploadId?: number | null
  initialCoverImageUrl?: string | null
  submitting: boolean
  submitLabel: string
  fieldErrors?: Record<string, string[]>
}>()

const emit = defineEmits<{
  submit: [payload: { name: string; description: string; coverUploadId: number | null }]
}>()

const name = ref(props.initialName ?? '')
const description = ref(props.initialDescription ?? '')
const coverUploadId = ref<number | null>(props.initialCoverUploadId ?? null)

// See register.vue: no native submit before the page is hydrated.
const hydrated = ref(false)
onMounted(() => (hydrated.value = true))

const descriptionError = computed(() =>
  props.fieldErrors?.description?.length ? `Description ${props.fieldErrors.description[0]}` : '',
)

function onSubmit() {
  emit('submit', {
    name: name.value,
    description: description.value,
    coverUploadId: coverUploadId.value,
  })
}
</script>

<template>
  <BForm method="post" novalidate @submit.prevent="onSubmit">
    <FormTextField id="name" v-model="name" label="Name" autofocus :errors="fieldErrors?.name" />
    <BFormGroup label="Description" label-for="description" class="mb-3">
      <BFormTextarea
        id="description"
        v-model="description"
        name="description"
        rows="3"
        :state="descriptionError ? false : null"
        required
      />
      <BFormInvalidFeedback :state="descriptionError ? false : null">
        {{ descriptionError }}
      </BFormInvalidFeedback>
    </BFormGroup>
    <ImageUpload v-model="coverUploadId" label="Cover image" :preview-url="initialCoverImageUrl" />
    <BButton type="submit" variant="primary" :disabled="submitting || !hydrated">
      {{ submitting ? 'Saving…' : submitLabel }}
    </BButton>
  </BForm>
</template>
