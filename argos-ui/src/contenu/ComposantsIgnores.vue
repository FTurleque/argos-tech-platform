<script setup lang="ts">
// Liste des composants d'un projet écartés de l'inventaire faute de PURL (INV-01-S3, décision E4 du
// 2026-11-19). Composant de présentation, sans appel réseau : les données viennent de
// `composantsIgnores` (composants-ignores.ts). Valeurs issues de la SBOM rendues comme texte, par interpolation.
import { computed } from 'vue'
import { syntheseComposantsIgnores, type ComposantsIgnoresVue } from './composants-ignores.ts'

const props = defineProps<{
  vue: ComposantsIgnoresVue
  /** Nom du projet, repris dans la légende du tableau. */
  projet: string
}>()

const synthese = computed(() => syntheseComposantsIgnores(props.vue))
// Clé de ligne : rang dans la liste du serveur (liste figée, jamais réordonnée) et identité du composant.
const composants = computed(() =>
  (props.vue.etat === 'DISPONIBLE' ? props.vue.composants : []).map((composant, rang) => ({
    ...composant,
    cle: `${rang}|${composant.groupe ?? ''}|${composant.nom ?? ''}|${composant.version ?? ''}`,
  })),
)
</script>

<template>
  <div class="composants-ignores" data-composants-ignores>
    <p class="muted" data-champ="synthese">{{ synthese }}</p>
    <table v-if="composants.length > 0">
      <caption class="sr-only">Composants ignorés du projet {{ projet }}</caption>
      <thead>
        <tr>
          <th scope="col">Nom</th>
          <th scope="col">Groupe</th>
          <th scope="col">Version</th>
          <th scope="col">Motif</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="composant in composants" :key="composant.cle" data-composant-ignore>
          <td>{{ composant.nom ?? 'non renseigné' }}</td>
          <td>{{ composant.groupe ?? 'non renseigné' }}</td>
          <td>{{ composant.version ?? 'non renseignée' }}</td>
          <td>{{ composant.motif }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
th, td { text-align: left; padding: 0.3rem 0.5rem; border-bottom: 1px solid var(--border); overflow-wrap: anywhere; }
th { color: var(--navy); }
</style>
