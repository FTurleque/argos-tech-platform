<script setup lang="ts">
// Vue d'un signal de sécurité (ALR-09, COL-02-S1, COL-03-S2) : provenance, gravité, résumé de la source, alias,
// paquets affectés, historique des versions et enrichissements, puis impacts du signal dans les espaces de
// l'utilisateur avec leur traitement (COR-09, COR-10, COR-12). Adresse des liens des alertes :
// `/#/signaux/{id}`. Titre, résumé et références viennent de la source : ils sont rendus comme texte.
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { estUuid } from '../api/recherche-criteres'
import { fetchSignal, type SignalDetail } from '../api/signaux'
import { messageEchec } from '../contenu/alimentation'
import TraitementImpacts from '../contenu/TraitementImpacts.vue'

const GRAVITES: Readonly<Record<string, string>> = {
  CRITICAL: 'critique',
  HIGH: 'élevée',
  MEDIUM: 'moyenne',
  MODERATE: 'modérée',
  LOW: 'faible',
}

const route = useRoute()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))
const detail = ref<SignalDetail | undefined>(undefined)
const erreur = ref('')
const chargement = ref(false)
let annulation = new AbortController()

function gravite(code: string | null): string {
  return code ? (GRAVITES[code] ?? code) : 'non précisée'
}

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('fr-FR') : 'inconnue'
}

async function charger(): Promise<void> {
  annulation.abort()
  annulation = new AbortController()
  const signal = annulation.signal
  detail.value = undefined
  erreur.value = ''
  chargement.value = true
  try {
    const detailLu = await fetchSignal(id.value, signal)
    if (!signal.aborted) detail.value = detailLu
  } catch (echec) {
    if (!signal.aborted) erreur.value = messageEchec(echec)
  } finally {
    if (!signal.aborted) chargement.value = false
  }
}

watch(id, charger, { immediate: true })
onBeforeUnmount(() => annulation.abort())
</script>

<template>
  <section class="card" aria-labelledby="titre-signal">
    <output v-if="chargement" class="muted">Chargement du signal…</output>
    <template v-if="erreur">
      <h1 id="titre-signal">Signal</h1>
      <p class="status status-down" role="alert">
        {{ erreur }}
        <button type="button" class="secondary" @click="charger">Réessayer</button>
      </p>
    </template>
    <article v-else-if="detail">
      <h1 id="titre-signal">{{ detail.signal.title ?? detail.signal.externalId ?? 'Signal sans titre' }}</h1>
      <p class="muted" data-provenance>
        {{ detail.signal.source }} {{ detail.signal.externalId }} · gravité {{ gravite(detail.signal.severity) }}
        · publié le {{ date(detail.signal.publishedAt) }} · collecté le {{ date(detail.signal.collectedAt) }}
      </p>
      <p v-if="detail.exploited" class="status status-down" data-exploite><strong>Exploitation connue</strong></p>
      <p v-if="detail.signal.summary" data-resume-source>{{ detail.signal.summary }}</p>
      <p v-if="detail.signal.aliases?.length" class="muted">Alias : {{ detail.signal.aliases.join(', ') }}</p>
      <h2>Paquets affectés</h2>
      <ul v-if="detail.signal.affected?.length">
        <li v-for="(paquet, rang) in detail.signal.affected" :key="`${rang}-${paquet.packageKey}`">
          {{ paquet.packageKey ?? paquet.name }}
          <span v-if="paquet.ecosystem" class="muted"> ({{ paquet.ecosystem }})</span>
          <span v-if="paquet.versions?.length" class="muted"> · versions : {{ paquet.versions.join(', ') }}</span>
        </li>
      </ul>
      <p v-else class="muted">Aucun paquet affecté déclaré par la source.</p>
      <h2>Historique</h2>
      <ul v-if="detail.versions?.length">
        <li v-for="version in detail.versions" :key="version.id">
          Version {{ version.versionNo }} · collectée le {{ date(version.collectedAt) }}
        </li>
      </ul>
      <p v-else class="muted">Aucune version historisée.</p>
      <template v-if="detail.enrichments?.length">
        <h2>Enrichissements</h2>
        <ul>
          <li v-for="(enrichissement, rang) in detail.enrichments" :key="`${rang}-${enrichissement.source}`">
            {{ enrichissement.source }} {{ enrichissement.reference }}
            <span class="muted"> · {{ date(enrichissement.fetchedAt) }}</span>
          </li>
        </ul>
      </template>
      <TraitementImpacts v-if="estUuid(detail.signal.id)" :signal-id="detail.signal.id" />
    </article>
  </section>
</template>
