<script setup lang="ts">
// Sélecteur central de la couche mise en page (ADR-0023, AFF-01, AFF-04) : affiche, pour la veille
// reçue, le composant qui correspond à l'identifiant de mise en page de la préférence. Un identifiant
// inconnu de cette IHM affiche la mise en page de repli « classique ».
// Tout écran qui affiche la veille passe par ce composant ; il ne lit ni l'API ni la préférence.
import { computed } from 'vue'
import type { VeilleAffichee } from './modele'
import { MISES_EN_PAGE } from './registre'
import { choisirComposant } from './selection'

const props = defineProps<{
  /** Identifiant de mise en page résolu par la couche contenu (`contenu/resolution.ts`). */
  identifiant: string
  veille: VeilleAffichee
  niveauTitre?: number
}>()

const choix = computed(() => choisirComposant(props.identifiant, MISES_EN_PAGE))
</script>

<template>
  <component
    :is="choix.composant"
    :veille="veille"
    :niveau-titre="niveauTitre"
    :data-mise-en-page-active="choix.identifiant"
  />
</template>
