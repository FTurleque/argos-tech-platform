<script setup lang="ts">
// Titre HTML de niveau variable (h1 à h6), pour qu'une mise en page s'insère dans un écran qui a déjà
// ses propres titres sans casser la hiérarchie (RGAA 9.1). Les attributs (id, class) sont transmis.
import { computed } from 'vue'

const props = defineProps<{ niveau: number }>()
const balise = computed(() => `h${Math.min(6, Math.max(1, Math.trunc(props.niveau)))}`)
</script>

<template>
  <component :is="balise"><slot /></component>
</template>
