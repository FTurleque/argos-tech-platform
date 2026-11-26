<script setup lang="ts">
// Alimentation de l'inventaire d'un projet (INV-12, INV-13) : rattachement et détachement d'un dépôt, import d'une
// SBOM CycloneDX JSON ou de manifestes de dépendances, relance de l'import depuis les dépôts. Chaque action affiche
// l'issue renvoyée par le serveur, ou son refus ; le serveur décide des droits (administrateur pour les dépôts,
// contributeur pour les imports) et de la validité des documents. Après une action aboutie, `modifie` est émis pour
// relire l'inventaire.
import { ref } from 'vue'
import { estUuid } from '../api/recherche-criteres'
import { detacherDepot, importerManifestes, importerSbom, rattacherDepot, relancerImport } from '../api/projets'
import {
  depotDeSaisie,
  libelleImport,
  lireManifestes,
  lireSbom,
  messageEchec,
  saisieDepotVide,
  type FichierTexte,
} from './alimentation'
import type { DepotAffiche } from './inventaire-depots'
import SaisieDepot from './SaisieDepot.vue'

const props = defineProps<{ projetId: string; depots: readonly DepotAffiche[] }>()
const emit = defineEmits<{ modifie: [] }>()

const saisie = ref(saisieDepotVide())
const fichierSbom = ref<FichierTexte | undefined>(undefined)
const fichiersManifestes = ref<FichierTexte[]>([])
const enCours = ref(false)
const issue = ref('')
const erreur = ref('')

function fichiersChoisis(evenement: Event): FichierTexte[] {
  const champ = evenement.target as HTMLInputElement | null
  return Array.from(champ?.files ?? [])
}

function choisirSbom(evenement: Event): void {
  fichierSbom.value = fichiersChoisis(evenement)[0]
}

function choisirManifestes(evenement: Event): void {
  fichiersManifestes.value = fichiersChoisis(evenement)
}

/** Exécute une action : une seule à la fois, issue ou erreur affichée, inventaire relu si elle aboutit. */
async function executer(action: () => Promise<string | undefined>): Promise<void> {
  if (enCours.value) return
  enCours.value = true
  issue.value = ''
  erreur.value = ''
  try {
    const resultat = await action()
    if (resultat !== undefined) {
      issue.value = resultat
      emit('modifie')
    }
  } catch (echec) {
    erreur.value = messageEchec(echec)
  } finally {
    enCours.value = false
  }
}

function rattacher(): Promise<void> {
  return executer(async () => {
    const depot = depotDeSaisie(saisie.value)
    if (depot === undefined) {
      erreur.value = 'Saisissez l’identifiant du dépôt à rattacher.'
      return undefined
    }
    const rattache = await rattacherDepot(props.projetId, depot)
    saisie.value = saisieDepotVide()
    return rattache.principal ? `Dépôt ${depot.depot} rattaché comme dépôt principal.` : `Dépôt ${depot.depot} rattaché.`
  })
}

function detacher(depot: DepotAffiche): Promise<void> {
  return executer(async () => {
    await detacherDepot(props.projetId, depot.cle)
    return `Dépôt ${depot.depot} détaché.`
  })
}

function importerDocumentSbom(): Promise<void> {
  return executer(async () => {
    const lecture = await lireSbom(fichierSbom.value)
    if ('erreur' in lecture) {
      erreur.value = lecture.erreur
      return undefined
    }
    return `SBOM : ${libelleImport(await importerSbom(props.projetId, lecture.document))}`
  })
}

function importerFichiersManifestes(): Promise<void> {
  return executer(async () => {
    if (fichiersManifestes.value.length === 0) {
      erreur.value = 'Choisissez au moins un manifeste de dépendances.'
      return undefined
    }
    const manifestes = await lireManifestes(fichiersManifestes.value)
    return `Manifestes : ${libelleImport(await importerManifestes(props.projetId, manifestes))}`
  })
}

function relancer(): Promise<void> {
  return executer(async () => {
    const relance = await relancerImport(props.projetId)
    const lignes = relance.depots.map((depot) => `${depot.repository ?? depot.repositoryId} : ${libelleImport(depot.result)}`)
    return lignes.length > 0 ? `Import relancé. ${lignes.join(' ')}` : 'Import relancé : aucun dépôt traité.'
  })
}
</script>

<template>
  <section aria-labelledby="titre-alimentation" class="alimentation" :aria-busy="enCours ? 'true' : undefined">
    <h2 id="titre-alimentation">Alimenter l’inventaire</h2>
    <output class="muted" aria-live="polite" data-issue="alimentation">{{ issue }}</output>
    <p v-if="erreur" class="status status-down" role="alert" data-erreur="alimentation">{{ erreur }}</p>

    <h3>Dépôts</h3>
    <ul v-if="depots.length > 0" class="depots">
      <li v-for="depot in depots" :key="depot.cle">
        {{ depot.depot }} <span class="muted">({{ depot.forge }})</span>
        <button
          v-if="estUuid(depot.cle)"
          type="button"
          class="secondary"
          :disabled="enCours"
          :aria-label="`Détacher le dépôt ${depot.depot}`"
          @click="detacher(depot)"
        >
          Détacher
        </button>
      </li>
    </ul>
    <p v-else class="muted">Aucun dépôt rattaché.</p>
    <form novalidate data-formulaire="rattachement" @submit.prevent="rattacher">
      <SaisieDepot v-model="saisie" prefixe="rattachement" obligatoire />
      <button type="submit" :disabled="enCours">Rattacher le dépôt</button>
    </form>
    <button type="button" class="secondary" :disabled="enCours" data-action="relancer" @click="relancer">
      Relancer l’import depuis les dépôts
    </button>

    <h3>Import manuel</h3>
    <form novalidate data-formulaire="sbom" @submit.prevent="importerDocumentSbom">
      <label for="fichier-sbom">SBOM CycloneDX (JSON)</label>
      <input id="fichier-sbom" type="file" accept=".json,application/json" @change="choisirSbom" />
      <button type="submit" :disabled="enCours">Importer la SBOM</button>
    </form>
    <form novalidate data-formulaire="manifestes" @submit.prevent="importerFichiersManifestes">
      <label for="fichiers-manifestes">Manifestes de dépendances</label>
      <input id="fichiers-manifestes" type="file" multiple @change="choisirManifestes" />
      <button type="submit" :disabled="enCours">Importer les manifestes</button>
    </form>
  </section>
</template>

<style scoped>
.alimentation form { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin: 0.5rem 0; }
.alimentation h3 { font-size: 1rem; margin: 1rem 0 0.25rem; color: var(--navy); }
.depots { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.25rem; }
.depots li { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; overflow-wrap: anywhere; }
</style>
