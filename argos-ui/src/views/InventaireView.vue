<script setup lang="ts">
// Vue d'inventaire d'un projet (INV-01-S3, INV-02, INV-03 ; décision E4 du porteur du projet, 2026-11-19),
// commune au portail web et au client desktop : synthèse de l'inventaire de référence et composants écartés
// faute de PURL, lus sur `GET /api/v1/projects/{projectId}/inventory`. Le projet est désigné par son
// identifiant dans l'adresse (`/inventaire/{id}`) : l'API n'expose pas la liste des projets de l'utilisateur.
// Les autres écrans y mènent par lien (« Projets concernés » du détail d'une alerte).
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchInventaireProjet } from '../api/inventaire'
import ComposantsIgnores from '../contenu/ComposantsIgnores.vue'
import type { ComposantsIgnoresVue } from '../contenu/composants-ignores'
import {
  chargerInventaireProjet,
  messageIdentifiantProjet,
  type ResumeInventaire,
} from '../contenu/inventaire-projet'

const route = useRoute()
const router = useRouter()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : null))

const saisie = ref('')
const erreurSaisie = ref('')
const resume = ref<ResumeInventaire | null>(null)
const ignores = ref<ComposantsIgnoresVue | null>(null)
const erreur = ref('')
const annonce = ref('')
const chargement = ref(false)
let annulation = new AbortController()

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('fr-FR') : 'inconnue'
}

async function charger(): Promise<void> {
  annulation.abort()
  annulation = new AbortController()
  const signal = annulation.signal
  resume.value = null
  ignores.value = null
  erreur.value = ''
  saisie.value = id.value ?? ''
  if (!id.value) {
    chargement.value = false
    annonce.value = ''
    return
  }
  chargement.value = true
  annonce.value = 'Chargement de l’inventaire…'
  const etat = await chargerInventaireProjet(id.value, fetchInventaireProjet, signal)
  if (etat === null) return
  chargement.value = false
  resume.value = etat.resume
  ignores.value = etat.ignores
  erreur.value = etat.erreur
  annonce.value = etat.erreur ? '' : 'Inventaire chargé.'
}

async function afficher(): Promise<void> {
  erreurSaisie.value = messageIdentifiantProjet(saisie.value)
  if (erreurSaisie.value) return
  const cible = saisie.value.trim().toLowerCase()
  if (cible === id.value) await charger()
  else await router.push(`/inventaire/${cible}`)
}

watch(id, charger, { immediate: true })
</script>

<template>
  <section class="card" aria-labelledby="titre-inventaire">
    <h1 id="titre-inventaire">Inventaire d’un projet</h1>
    <p class="muted">
      Inventaire de référence importé pour un projet de vos espaces, et composants des SBOM écartés faute de PURL
      exploitable : aucun impact ne peut les concerner.
    </p>
    <form class="inventaire-choix" novalidate @submit.prevent="afficher">
      <label for="identifiant-projet">Identifiant du projet</label>
      <input
        id="identifiant-projet"
        v-model="saisie"
        type="text"
        autocomplete="off"
        spellcheck="false"
        :aria-invalid="erreurSaisie ? 'true' : undefined"
        aria-describedby="erreur-identifiant-projet"
      />
      <button type="submit">Afficher l’inventaire</button>
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
      <section v-if="ignores?.etat === 'DISPONIBLE'" aria-labelledby="titre-composants-ignores">
        <h2 id="titre-composants-ignores">Composants ignorés</h2>
        <ComposantsIgnores :vue="ignores" :projet="id ?? ''" />
      </section>
    </template>
  </section>
</template>

<style scoped>
.inventaire-choix { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin-bottom: 1rem; }
.inventaire-choix input { min-width: 22rem; max-width: 100%; }
.erreur-saisie { flex-basis: 100%; margin: 0; color: var(--ko); }
.inventaire-resume { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem; }
.inventaire-resume dt { font-weight: 600; }
.inventaire-resume dd { margin: 0; overflow-wrap: anywhere; }
</style>
