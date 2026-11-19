<script setup lang="ts">
// Page des composants inventoriés d'un projet (INV-06 ; décision F5 du porteur du projet, 2026-11-19), avec les
// dépôts de chaque composant pour un projet à plusieurs dépôts (INV-10, décision J6 du 2026-11-19).
// Composant de présentation, sans appel réseau : les données viennent de `pageComposants`
// (inventaire-listes.ts) ; la vue demande une autre page par l'événement `page`. Valeurs issues de la SBOM
// rendues comme texte, par interpolation. Les boutons de pagination restent focalisables à la première et à la
// dernière page (`aria-disabled`) : le focus clavier n'est jamais perdu lors d'un changement de page.
import { computed } from 'vue'
import { libellePage, syntheseComposants, type PageComposants } from './inventaire-listes.ts'
import { libelleDepotsComposant } from './inventaire-depots.ts'

const props = defineProps<{
  page: PageComposants
  /** Nom du projet, repris dans la légende du tableau. */
  projet: string
  /** Vrai pendant le chargement d'une autre page : les boutons ne déclenchent rien. */
  chargement?: boolean
  /** Vrai pour un projet à plusieurs dépôts : colonne « Dépôts » affichée (INV-10). */
  afficherDepots?: boolean
}>()

const emit = defineEmits<{ page: [numero: number] }>()

const synthese = computed(() => syntheseComposants(props.page))
const position = computed(() => libellePage(props.page))
const premiere = computed(() => props.page.page <= 0)
const derniere = computed(() => props.page.page >= props.page.pages - 1)

function aller(numero: number, bloque: boolean): void {
  if (bloque || props.chargement) return
  emit('page', numero)
}
</script>

<template>
  <div class="composants-inventories" data-composants-inventories>
    <p class="muted" data-champ="synthese-composants">{{ synthese }}</p>
    <table v-if="page.composants.length > 0" tabindex="-1" data-tableau-composants>
      <caption class="sr-only">Composants inventoriés du projet {{ projet }}, {{ position.toLowerCase() }}</caption>
      <thead>
        <tr>
          <th scope="col">Nom</th>
          <th scope="col">Version</th>
          <th scope="col">PURL</th>
          <th scope="col">Dépendance</th>
          <th v-if="afficherDepots" scope="col">Dépôts</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in page.composants" :key="c.cle" data-composant-inventorie>
          <td>{{ c.nom ?? 'non renseigné' }}</td>
          <td>{{ c.version ?? 'non renseignée' }}</td>
          <td class="purl">{{ c.purl ?? 'non renseigné' }}</td>
          <td>{{ c.dependance }}</td>
          <td v-if="afficherDepots" data-champ="depots-composant">{{ libelleDepotsComposant(c.depots) }}</td>
        </tr>
      </tbody>
    </table>
    <nav v-if="page.pages > 1" class="pagination" aria-label="Pagination des composants inventoriés">
      <button
        type="button"
        class="secondary"
        :aria-disabled="premiere || chargement ? 'true' : undefined"
        @click="aller(page.page - 1, premiere)"
      >
        Page précédente
      </button>
      <span data-champ="position-page">{{ position }}</span>
      <button
        type="button"
        class="secondary"
        :aria-disabled="derniere || chargement ? 'true' : undefined"
        @click="aller(page.page + 1, derniere)"
      >
        Page suivante
      </button>
    </nav>
  </div>
</template>

<style scoped>
table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
table:focus { outline: 2px solid var(--navy); outline-offset: 2px; }
th, td { text-align: left; padding: 0.3rem 0.5rem; border-bottom: 1px solid var(--border); overflow-wrap: anywhere; }
th { color: var(--navy); }
.purl { font-family: ui-monospace, monospace; font-size: 0.85rem; }
.pagination { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; margin-top: 0.5rem; }
.pagination button[aria-disabled='true'] { opacity: 0.55; cursor: not-allowed; }
</style>
