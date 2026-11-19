<script setup lang="ts">
// Alertes du poste (canal DESKTOP) de l'utilisateur : la liste, ou le détail d'une alerte quand la
// route porte son identifiant (NOT-01-S2 : une alerte non notifiée reste visible ici ; NOT-02 :
// le clic sur une notification ouvre cette vue sur l'alerte).
// Le détail porte aussi la section « Composants ignorés » des projets cités (INV-01-S3, décision E4 du
// 2026-11-19) : argos-ui n'a pas de vue d'inventaire ni d'écran de projet ; ce détail, dont la section
// « Projets concernés » nomme chaque projet touché, en est l'écran le plus proche. L'inventaire d'un projet
// n'est demandé qu'à l'ouverture de sa ligne.
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, fetchAlerte, fetchAlertesPoste, type AlerteVue } from '../api/client'
import { fetchInventaireProjet } from '../api/inventaire'
import { estUuid } from '../api/recherche-criteres'
import ComposantsIgnores from '../contenu/ComposantsIgnores.vue'
import { composantsIgnores, messageErreurInventaire, type ComposantsIgnoresVue } from '../contenu/composants-ignores'
import { urlSure } from '../contenu/liens'
import { alerteConsultee } from '../contenu/suivi-poste'
import { resumeIaAffiche } from '../contenu/resume-ia'

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

/** Projets distincts cités par l'alerte, dans l'ordre de première citation. */
const projetsCites = computed(() => {
  const vus = new Map<string, string>()
  for (const element of alerte.value?.contenu?.elements ?? []) {
    for (const p of element.projets ?? []) {
      if (estUuid(p.id) && !vus.has(p.id)) vus.set(p.id, p.nom ?? p.id)
    }
  }
  return [...vus].map(([id, nom]) => ({ id, nom }))
})

/** Inventaire chargé à la demande, par projet : en cours, erreur ou composants ignorés. */
interface EtatInventaire {
  chargement: boolean
  erreur: string
  vue: ComposantsIgnoresVue | null
}
const inventaires = reactive<Record<string, EtatInventaire>>({})
let annulation = new AbortController()

function oublierInventaires(): void {
  annulation.abort()
  annulation = new AbortController()
  for (const cle of Object.keys(inventaires)) delete inventaires[cle]
}

async function ouvrirInventaire(projetId: string, evenement: Event): Promise<void> {
  if (!(evenement.target as HTMLDetailsElement | null)?.open || inventaires[projetId]) return
  const etat: EtatInventaire = reactive({ chargement: true, erreur: '', vue: null })
  inventaires[projetId] = etat
  try {
    etat.vue = composantsIgnores(await fetchInventaireProjet(projetId, annulation.signal))
  } catch (e) {
    if (e instanceof ApiError && e.motif === 'ANNULE') return
    etat.erreur = messageErreurInventaire(e instanceof ApiError ? e.status : undefined)
  } finally {
    etat.chargement = false
  }
}

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
  oublierInventaires()
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
        <div v-if="resumeIa" class="resume-ia">
          <h2>Résumé</h2>
          <p class="muted" data-mention="ia">{{ resumeIa.mention }}, à vérifier sur les sources ci-dessus</p>
          <p>{{ resumeIa.texte }}</p>
        </div>
        <section v-if="projetsCites.length > 0" :key="alerte.id" aria-labelledby="titre-composants-ignores">
          <h2 id="titre-composants-ignores">Composants ignorés</h2>
          <p class="muted">
            Composants des SBOM écartés de l'inventaire faute de PURL exploitable, au dernier import :
            aucun impact ne peut les concerner.
          </p>
          <details
            v-for="projet in projetsCites"
            :key="projet.id"
            class="inventaire-projet"
            @toggle="ouvrirInventaire(projet.id, $event)"
          >
            <summary>{{ projet.nom }}</summary>
            <p v-if="inventaires[projet.id]?.chargement" class="muted" role="status">Chargement de l'inventaire…</p>
            <p v-else-if="inventaires[projet.id]?.erreur" class="status status-down">{{ inventaires[projet.id].erreur }}</p>
            <ComposantsIgnores
              v-else-if="inventaires[projet.id]?.vue"
              :vue="inventaires[projet.id].vue!"
              :projet="projet.nom"
            />
          </details>
        </section>
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
