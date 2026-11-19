<script setup lang="ts">
// Alertes du poste (canal DESKTOP) de l'utilisateur : la liste, ou le détail d'une alerte quand la
// route porte son identifiant (NOT-01-S2 : une alerte non notifiée reste visible ici ; NOT-02 :
// le clic sur une notification ouvre cette vue sur l'alerte).
// Chaque projet cité mène à sa vue d'inventaire (`/inventaire/{id}`, décision E4 du 2026-11-19), qui
// affiche notamment ses composants écartés faute de PURL.
// Sous chaque signal, les impacts visibles de l'utilisateur avec leur état de traitement, leur historique et les
// transitions qu'il peut demander (COR-09, COR-10, COR-12 ; changement automatiser-chaine-veille, tâche 3.5). Une
// alerte `ANNULEE` (impacts écartés ou résolus avant la remise, ALR-07) reste listée avec cette mention.
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, fetchAlerte, fetchAlertesPoste, type AlerteVue } from '../api/client'
import { STATUT_INTROUVABLE, STATUT_NON_AUTHENTIFIE } from '../api/statuts-http'
import { estUuid } from '../api/recherche-criteres'
import { urlSure } from '../contenu/liens'
import { alerteConsultee } from '../contenu/suivi-poste'
import { resumeIaAffiche } from '../contenu/resume-ia'
import TraitementImpacts from '../contenu/TraitementImpacts.vue'

const GRAVITES: Record<string, string> = {
  CRITICAL: 'critique',
  HIGH: 'élevée',
  MEDIUM: 'moyenne',
  MODERATE: 'modérée',
  LOW: 'faible',
}

const route = useRoute()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : null))
const liste = ref<AlerteVue[]>([])
const alerte = ref<AlerteVue | null>(null)
const erreur = ref('')
// Résumé IA du détail : toujours avec sa mention et après les sources sur lesquelles il s'appuie (IAG-05).
const resumeIa = computed(() => resumeIaAffiche(alerte.value?.contenu))

function gravite(code: string | null): string {
  return code ? (GRAVITES[code] ?? code) : 'non précisée'
}

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('fr-FR') : ''
}

function message(e: unknown): string {
  if (e instanceof ApiError && e.status === STATUT_NON_AUTHENTIFIE) return 'Connectez-vous pour consulter vos alertes.'
  if (e instanceof ApiError && e.status === STATUT_INTROUVABLE) return 'Alerte introuvable.'
  return e instanceof ApiError ? e.message : 'Erreur inattendue'
}

async function charger(): Promise<void> {
  erreur.value = ''
  try {
    if (id.value) {
      alerte.value = await fetchAlerte(id.value)
      alerteConsultee(id.value)
    } else {
      alerte.value = null
      liste.value = await fetchAlertesPoste()
    }
  } catch (e) {
    erreur.value = message(e)
  }
}

watch(id, charger, { immediate: true })
</script>

<template>
  <section class="card">
    <template v-if="id">
      <p><RouterLink to="/alertes">← Toutes les alertes</RouterLink></p>
      <p v-if="erreur" class="status status-down">{{ erreur }}</p>
      <article v-else-if="alerte">
        <h1>{{ alerte.titre }}</h1>
        <p class="muted">Gravité {{ gravite(alerte.gravite) }} · {{ date(alerte.creeLe) }}</p>
        <div v-for="element in alerte.contenu?.elements ?? []" :key="element.signalId">
          <h2>Projets concernés</h2>
          <ul>
            <li v-for="projet in element.projets" :key="projet.id">
              <RouterLink v-if="estUuid(projet.id)" :to="`/inventaire/${projet.id}`">{{ projet.nom }}</RouterLink>
              <template v-else>{{ projet.nom }}</template>
              ({{ projet.niveau }})
            </li>
          </ul>
          <h2>Sources</h2>
          <ul>
            <li v-for="source in element.sources" :key="`${source.nom}-${source.identifiant}`">
              <a v-if="urlSure(source.url)" :href="urlSure(source.url) ?? undefined" target="_blank" rel="noopener noreferrer">
                {{ source.nom }} {{ source.identifiant }}
              </a>
              <span v-else>{{ source.nom }} {{ source.identifiant }}</span>
            </li>
          </ul>
          <!-- État de traitement, historique et transitions des impacts du signal (COR-09, COR-10, COR-12). -->
          <TraitementImpacts v-if="estUuid(element.signalId)" :signal-id="element.signalId" />
        </div>
        <div v-if="resumeIa" class="resume-ia">
          <h2>Résumé</h2>
          <p class="muted" data-mention="ia">{{ resumeIa.mention }}, à vérifier sur les sources ci-dessus</p>
          <p>{{ resumeIa.texte }}</p>
        </div>
      </article>
    </template>
    <template v-else>
      <h1>Alertes du poste</h1>
      <p v-if="erreur" class="status status-down">{{ erreur }}</p>
      <p v-else-if="liste.length === 0" class="muted">Aucune alerte.</p>
      <ul v-else>
        <li v-for="a in liste" :key="a.id">
          <RouterLink :to="`/alertes/${a.id}`">{{ a.titre }}</RouterLink>
          <span class="muted"> · gravité {{ gravite(a.gravite) }} · {{ date(a.creeLe) }}</span>
          <span v-if="a.etat === 'ANNULEE'" class="muted"> · sans objet : impacts écartés ou résolus avant la remise</span>
        </li>
      </ul>
    </template>
  </section>
</template>
