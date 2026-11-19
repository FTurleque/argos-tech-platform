<script setup lang="ts">
// Vue d'inventaire d'un projet (INV-01-S3, INV-02, INV-03, INV-06 ; décisions E4, F1, F5 et G5 du porteur du
// projet, 2026-11-19), commune au portail web et au client desktop : choix du projet parmi les projets visibles
// (`GET /api/v1/projects`), synthèse de l'inventaire de référence et composants écartés faute de PURL, sans la
// liste des composants (`GET /api/v1/projects/{projectId}/inventory/summary`), page de composants inventoriés
// affichée (`GET /api/v1/projects/{projectId}/inventory/components`). Le projet affiché est celui de l'adresse
// (`/inventaire/{id}`) ; il se choisit dans la liste, ou se désigne par son identifiant (projet au-delà de la
// liste, lien d'une alerte). Les autres écrans y mènent par lien (« Projets concernés » du détail d'une alerte).
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchComposantsProjet, fetchProjets, fetchSyntheseInventaire } from '../api/inventaire'
import ComposantsIgnores from '../contenu/ComposantsIgnores.vue'
import ComposantsInventories from '../contenu/ComposantsInventories.vue'
import type { ComposantsIgnoresVue } from '../contenu/composants-ignores'
import {
  chargerComposants,
  chargerProjets,
  libellePage,
  syntheseProjets,
  type ListeProjets,
  type PageComposants,
} from '../contenu/inventaire-listes'
import {
  chargerInventaireProjet,
  messageIdentifiantProjet,
  type ResumeInventaire,
} from '../contenu/inventaire-projet'

const route = useRoute()
const router = useRouter()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : null))

const projets = ref<ListeProjets | null>(null)
const erreurProjets = ref('')
const choix = ref('')
const erreurChoix = ref('')
const saisie = ref('')
const erreurSaisie = ref('')
const resume = ref<ResumeInventaire | null>(null)
const ignores = ref<ComposantsIgnoresVue | null>(null)
const erreur = ref('')
const annonce = ref('')
const chargement = ref(false)
const composants = ref<PageComposants | null>(null)
const erreurComposants = ref('')
const chargementComposants = ref(false)
const sectionComposants = ref<HTMLElement | null>(null)
let annulation = new AbortController()
let annulationComposants = new AbortController()
let annulationProjets = new AbortController()

/** Libellé du projet affiché : celui de la liste s'il y figure, son identifiant sinon. */
const nomProjet = computed(() => projets.value?.options.find((option) => option.id === id.value)?.libelle ?? id.value ?? '')

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('fr-FR') : 'inconnue'
}

async function chargerListe(): Promise<void> {
  annulationProjets.abort()
  annulationProjets = new AbortController()
  erreurProjets.value = ''
  const etat = await chargerProjets(fetchProjets, annulationProjets.signal)
  if (etat === null) return
  projets.value = etat.projets
  erreurProjets.value = etat.erreur
}

async function chargerPage(numero: number, focaliser: boolean): Promise<void> {
  annulationComposants.abort()
  annulationComposants = new AbortController()
  const signal = annulationComposants.signal
  const projet = id.value
  if (!projet) return
  erreurComposants.value = ''
  chargementComposants.value = true
  const etat = await chargerComposants(projet, numero, fetchComposantsProjet, signal)
  if (etat === null) return
  chargementComposants.value = false
  composants.value = etat.page
  erreurComposants.value = etat.erreur
  if (etat.page && focaliser) {
    annonce.value = `${libellePage(etat.page)} des composants inventoriés chargée.`
    await nextTick()
    sectionComposants.value?.querySelector<HTMLElement>('[data-tableau-composants]')?.focus()
  }
}

async function charger(): Promise<void> {
  annulation.abort()
  annulationComposants.abort()
  annulation = new AbortController()
  const signal = annulation.signal
  resume.value = null
  ignores.value = null
  composants.value = null
  erreur.value = ''
  erreurComposants.value = ''
  chargementComposants.value = false
  saisie.value = id.value ?? ''
  choix.value = id.value ?? ''
  erreurSaisie.value = ''
  erreurChoix.value = ''
  if (!id.value) {
    chargement.value = false
    annonce.value = ''
    return
  }
  chargement.value = true
  annonce.value = 'Chargement de l’inventaire…'
  const etat = await chargerInventaireProjet(id.value, fetchSyntheseInventaire, signal)
  if (etat === null) return
  chargement.value = false
  resume.value = etat.resume
  ignores.value = etat.ignores
  erreur.value = etat.erreur
  annonce.value = etat.erreur ? '' : 'Inventaire chargé.'
  if (etat.resume?.etat === 'DISPONIBLE') await chargerPage(0, false)
}

async function aller(cible: string): Promise<void> {
  if (cible === id.value) await charger()
  else await router.push(`/inventaire/${cible}`)
}

async function choisir(): Promise<void> {
  erreurChoix.value = choix.value ? '' : 'Choisissez un projet dans la liste.'
  if (erreurChoix.value) return
  await aller(choix.value)
}

async function afficher(): Promise<void> {
  erreurSaisie.value = messageIdentifiantProjet(saisie.value)
  if (erreurSaisie.value) return
  await aller(saisie.value.trim().toLowerCase())
}

watch(id, charger, { immediate: true })
onMounted(chargerListe)
onBeforeUnmount(() => {
  annulation.abort()
  annulationComposants.abort()
  annulationProjets.abort()
})
</script>

<template>
  <section class="card" aria-labelledby="titre-inventaire">
    <h1 id="titre-inventaire">Inventaire d’un projet</h1>
    <p class="muted">
      Inventaire de référence importé pour un projet de vos espaces, composants inventoriés, et composants des SBOM
      écartés faute de PURL exploitable : aucun impact ne peut concerner ces derniers.
    </p>
    <form class="inventaire-choix" novalidate @submit.prevent="choisir">
      <label for="choix-projet">Projet</label>
      <select
        id="choix-projet"
        v-model="choix"
        :aria-invalid="erreurChoix ? 'true' : undefined"
        aria-describedby="etat-projets erreur-choix-projet"
      >
        <option value="">— Choisir un projet —</option>
        <option v-for="o in projets?.options ?? []" :key="o.id" :value="o.id">{{ o.libelle }}</option>
      </select>
      <button type="submit">Afficher l’inventaire</button>
      <p id="etat-projets" class="muted aide">
        <template v-if="projets">{{ syntheseProjets(projets) }}</template>
        <template v-else-if="!erreurProjets">Chargement de la liste des projets…</template>
      </p>
      <p id="erreur-choix-projet" class="erreur-saisie" role="alert">{{ erreurChoix }}</p>
    </form>
    <div role="alert">
      <p v-if="erreurProjets" class="status status-down">
        {{ erreurProjets }}
        <button type="button" class="secondary" @click="chargerListe">Recharger la liste</button>
      </p>
    </div>
    <form class="inventaire-choix" novalidate @submit.prevent="afficher">
      <label for="identifiant-projet">Ou identifiant du projet</label>
      <input
        id="identifiant-projet"
        v-model="saisie"
        type="text"
        autocomplete="off"
        spellcheck="false"
        :aria-invalid="erreurSaisie ? 'true' : undefined"
        aria-describedby="erreur-identifiant-projet"
      />
      <button type="submit" class="secondary">Afficher par identifiant</button>
      <p id="erreur-identifiant-projet" class="erreur-saisie" role="alert">{{ erreurSaisie }}</p>
    </form>

    <output class="sr-only" aria-live="polite">{{ annonce }}</output>
    <p v-if="chargement" class="muted">Chargement de l’inventaire…</p>
    <div role="alert">
      <p v-if="erreur" class="status status-down">
        {{ erreur }}
        <button type="button" class="secondary" @click="charger">Réessayer</button>
      </p>
    </div>

    <template v-if="resume">
      <section aria-labelledby="titre-inventaire-reference">
        <h2 id="titre-inventaire-reference">Inventaire de référence</h2>
        <p v-if="resume.etat === 'SANS_INVENTAIRE'" class="muted">Aucun inventaire importé pour ce projet.</p>
        <dl v-else class="inventaire-resume">
          <dt>Origine</dt>
          <dd data-champ="origine">{{ resume.origine }}</dd>
          <dt>Date de la source</dt>
          <dd data-champ="date-source">
            {{ date(resume.dateSource) }}<template v-if="resume.ageJours !== null"> ({{ resume.ageJours }} jours)</template>
            <strong v-if="resume.obsolete"> · obsolète</strong>
          </dd>
          <dt>Importé le</dt>
          <dd data-champ="importe-le">{{ date(resume.importeLe) }}</dd>
          <dt>Composants inventoriés</dt>
          <dd data-champ="composants">{{ resume.composants }}</dd>
        </dl>
      </section>
      <section
        v-if="resume.etat === 'DISPONIBLE'"
        ref="sectionComposants"
        aria-labelledby="titre-composants-inventories"
        :aria-busy="chargementComposants ? 'true' : undefined"
      >
        <h2 id="titre-composants-inventories">Composants inventoriés</h2>
        <p v-if="chargementComposants && !composants" class="muted">Chargement des composants…</p>
        <div role="alert">
          <p v-if="erreurComposants" class="status status-down">
            {{ erreurComposants }}
            <button type="button" class="secondary" @click="chargerPage(composants?.page ?? 0, true)">Réessayer</button>
          </p>
        </div>
        <ComposantsInventories
          v-if="composants"
          :page="composants"
          :projet="nomProjet"
          :chargement="chargementComposants"
          @page="(numero) => chargerPage(numero, true)"
        />
      </section>
      <section v-if="ignores?.etat === 'DISPONIBLE'" aria-labelledby="titre-composants-ignores">
        <h2 id="titre-composants-ignores">Composants ignorés</h2>
        <ComposantsIgnores :vue="ignores" :projet="id ?? ''" />
      </section>
    </template>
  </section>
</template>

<style scoped>
.inventaire-choix { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin-bottom: 1rem; }
.inventaire-choix input, .inventaire-choix select { min-width: 22rem; max-width: 100%; }
.erreur-saisie { flex-basis: 100%; margin: 0; color: var(--ko); }
.aide { flex-basis: 100%; margin: 0; }
.inventaire-resume { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem; }
.inventaire-resume dt { font-weight: 600; }
.inventaire-resume dd { margin: 0; overflow-wrap: anywhere; }
</style>
