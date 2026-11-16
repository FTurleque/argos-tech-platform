<script setup lang="ts">
// Alertes du poste (canal DESKTOP) de l'utilisateur : la liste, ou le détail d'une alerte quand la
// route porte son identifiant (NOT-01-S2 : une alerte non notifiée reste visible ici ; NOT-02 :
// le clic sur une notification ouvre cette vue sur l'alerte).
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, fetchAlerte, fetchAlertesPoste, type AlerteVue } from '../api/client'
import { urlSure } from '../contenu/liens'
import { alerteConsultee } from '../contenu/suivi-poste'

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

function gravite(code: string | null): string {
  return code ? (GRAVITES[code] ?? code) : 'non précisée'
}

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('fr-FR') : ''
}

function message(e: unknown): string {
  if (e instanceof ApiError && e.status === 401) return 'Connectez-vous pour consulter vos alertes.'
  if (e instanceof ApiError && e.status === 404) return 'Alerte introuvable.'
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
            <li v-for="projet in element.projets" :key="projet.id">{{ projet.nom }} ({{ projet.niveau }})</li>
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
        </div>
        <p v-if="alerte.contenu?.resumeIa">{{ alerte.contenu.resumeIa }}</p>
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
        </li>
      </ul>
    </template>
  </section>
</template>
