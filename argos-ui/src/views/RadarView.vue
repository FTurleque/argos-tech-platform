<script setup lang="ts">
// Vue radar d'un espace (RAD-09, ALR-09) : échéances par horizon, dettes de version des projets, référentiel des
// standards et suggestions de l'IA en attente. Adresse des rappels du radar : `/#/espaces/{id}/radar`. La décision
// sur une suggestion n'est proposée qu'à l'administrateur de l'espace (rôle lu par `GET /api/v1/espaces/{id}`) ; le
// serveur reste seul juge des droits. Les sections se chargent indépendamment : l'échec de l'une n'empêche pas
// l'affichage des autres.
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchEspace, type EspaceDeCompte } from '../api/espaces'
import {
  deciderSuggestion,
  fetchDettes,
  fetchEcheances,
  fetchStandards,
  fetchSuggestions,
  HORIZONS,
  type DecisionSuggestion,
  type DetteVersion,
  type EntreeReferentiel,
  type Horizon,
  type Radar,
  type SuggestionReferentiel,
} from '../api/radar'
import { messageEchec } from '../contenu/alimentation'
import { MENTION_IA } from '../contenu/resume-ia'

const LIBELLES_HORIZON: Readonly<Record<Horizon, string>> = { '30j': '30 jours', '6m': '6 mois', '1a': '1 an' }
const LIBELLES_NATURE: Readonly<Record<string, string>> = {
  SORTIE: 'sortie',
  CORRECTIF: 'correctif',
  FIN_DE_SUPPORT: 'fin de support',
}
const ETAT_EN_ATTENTE = 'EN_ATTENTE'

const route = useRoute()
const espaceId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const espace = ref<EspaceDeCompte | undefined>(undefined)
const erreurEspace = ref('')
const horizon = ref<Horizon>('30j')
const radar = ref<Radar | undefined>(undefined)
const erreurRadar = ref('')
const dettes = ref<DetteVersion[]>([])
const erreurDettes = ref('')
const standards = ref<EntreeReferentiel[]>([])
const erreurStandards = ref('')
const suggestions = ref<SuggestionReferentiel[]>([])
const erreurSuggestions = ref('')
const decisionEnCours = ref(false)
const issueDecision = ref('')
const erreurDecision = ref('')
let annulation = new AbortController()

const administrateur = computed(() => espace.value?.role === 'ADMINISTRATEUR')
const enAttente = computed(() => suggestions.value.filter((suggestion) => suggestion.etat === ETAT_EN_ATTENTE))

function nature(code: string): string {
  return LIBELLES_NATURE[code] ?? code
}

/** Lit une section ; son erreur est rangée à part, sauf si le chargement a été remplacé entre-temps. */
async function section<T>(lire: () => Promise<T>, appliquer: (valeur: T) => void, erreur: { value: string }): Promise<void> {
  const signal = annulation.signal
  erreur.value = ''
  try {
    const valeur = await lire()
    if (!signal.aborted) appliquer(valeur)
  } catch (echec) {
    if (!signal.aborted) erreur.value = messageEchec(echec)
  }
}

function chargerEcheances(): Promise<void> {
  return section(() => fetchEcheances(espaceId.value, horizon.value, annulation.signal), (valeur) => (radar.value = valeur), erreurRadar)
}

function chargerStandards(): Promise<void> {
  return section(() => fetchStandards(espaceId.value, annulation.signal), (valeur) => (standards.value = valeur), erreurStandards)
}

async function charger(): Promise<void> {
  annulation.abort()
  annulation = new AbortController()
  espace.value = undefined
  radar.value = undefined
  issueDecision.value = ''
  erreurDecision.value = ''
  await Promise.all([
    section(() => fetchEspace(espaceId.value, annulation.signal), (valeur) => (espace.value = valeur), erreurEspace),
    chargerEcheances(),
    section(() => fetchDettes(espaceId.value, annulation.signal), (valeur) => (dettes.value = valeur), erreurDettes),
    chargerStandards(),
    section(() => fetchSuggestions(espaceId.value, annulation.signal), (valeur) => (suggestions.value = valeur), erreurSuggestions),
  ])
}

async function decider(suggestion: SuggestionReferentiel, decision: DecisionSuggestion): Promise<void> {
  if (decisionEnCours.value) return
  decisionEnCours.value = true
  issueDecision.value = ''
  erreurDecision.value = ''
  try {
    suggestions.value = await deciderSuggestion(espaceId.value, suggestion.id, decision)
    const verbe = decision === 'ACCEPTER' ? 'acceptée' : 'refusée'
    issueDecision.value = `Suggestion ${verbe} pour ${suggestion.cle}.`
    await chargerStandards()
  } catch (echec) {
    erreurDecision.value = messageEchec(echec)
  } finally {
    decisionEnCours.value = false
  }
}

watch(espaceId, charger, { immediate: true })
watch(horizon, () => void chargerEcheances())
onBeforeUnmount(() => annulation.abort())
</script>

<template>
  <section class="card" aria-labelledby="titre-radar">
    <h1 id="titre-radar">Radar de l’espace {{ espace?.nom ?? '' }}</h1>
    <p class="muted">
      Sorties, correctifs et fins de support des technologies du référentiel de l’espace, dettes de version de ses
      projets et suggestions de l’IA en attente de décision.
    </p>
    <p v-if="erreurEspace" class="status status-down" role="alert" data-erreur="espace">{{ erreurEspace }}</p>
  </section>

  <section class="card" aria-labelledby="titre-echeances">
    <h2 id="titre-echeances">Échéances</h2>
    <label for="horizon-radar">Horizon</label>
    <select id="horizon-radar" v-model="horizon">
      <option v-for="valeur in HORIZONS" :key="valeur" :value="valeur">{{ LIBELLES_HORIZON[valeur] }}</option>
    </select>
    <p v-if="erreurRadar" class="status status-down" role="alert" data-erreur="echeances">{{ erreurRadar }}</p>
    <template v-else-if="radar">
      <p v-if="radar.echeances.length === 0" class="muted">Aucune échéance du {{ radar.depuis }} au {{ radar.jusqua }}.</p>
      <ul v-else data-liste="echeances">
        <li v-for="echeance in radar.echeances" :key="`${echeance.cle}-${echeance.version}-${echeance.nature}-${echeance.date}`">
          <strong>{{ echeance.date }}</strong> · {{ echeance.libelle ?? echeance.cle }}
          <template v-if="echeance.version"> {{ echeance.version }}</template> · {{ nature(echeance.nature) }}
          <span v-if="echeance.source" class="muted"> · source : {{ echeance.source }}</span>
          <span v-if="echeance.note" class="muted"> · {{ echeance.note }}</span>
        </li>
      </ul>
      <p v-for="produit in radar.sources.filter((source) => source.derniereErreur)" :key="produit.produit" class="muted">
        Calendrier de {{ produit.produit }} : {{ produit.derniereErreur }}
      </p>
    </template>
  </section>

  <section class="card" aria-labelledby="titre-dettes">
    <h2 id="titre-dettes">Dettes de version</h2>
    <p v-if="erreurDettes" class="status status-down" role="alert">{{ erreurDettes }}</p>
    <p v-else-if="dettes.length === 0" class="muted">Aucune dette de version.</p>
    <ul v-else data-liste="dettes">
      <li v-for="dette in dettes" :key="dette.id">
        <RouterLink :to="`/inventaire/${dette.projetId}`">{{ dette.projet ?? dette.projetId }}</RouterLink> :
        {{ dette.cle }} {{ dette.version }}
        <span class="muted">
          <template v-if="dette.finSupport"> · fin de support le {{ dette.finSupport }}</template>
          <template v-if="dette.versionStandard"> · standard de l’espace : {{ dette.versionStandard }}</template>
        </span>
      </li>
    </ul>
  </section>

  <section class="card" aria-labelledby="titre-referentiel">
    <h2 id="titre-referentiel">Référentiel des standards</h2>
    <p v-if="erreurStandards" class="status status-down" role="alert">{{ erreurStandards }}</p>
    <p v-else-if="standards.length === 0" class="muted">Référentiel vide.</p>
    <table v-else data-tableau="referentiel">
      <caption class="sr-only">Technologies du référentiel et leur statut</caption>
      <thead>
        <tr>
          <th scope="col">Technologie</th>
          <th scope="col">Statut</th>
          <th scope="col">Version recommandée</th>
          <th scope="col">Responsable</th>
          <th scope="col">Revue</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entree in standards" :key="entree.id" :data-standard="entree.cle">
          <td>{{ entree.libelle ?? entree.cle }}</td>
          <td>{{ entree.statut }}</td>
          <td>{{ entree.versionRecommandee ?? '—' }}</td>
          <td>{{ entree.responsable ?? '—' }}</td>
          <td>{{ entree.dateRevue ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
  </section>

  <section class="card" aria-labelledby="titre-suggestions" :aria-busy="decisionEnCours ? 'true' : undefined">
    <h2 id="titre-suggestions">Suggestions en attente</h2>
    <p class="muted" role="status" aria-live="polite" data-issue="decision">{{ issueDecision }}</p>
    <p v-if="erreurDecision" class="status status-down" role="alert" data-erreur="decision">{{ erreurDecision }}</p>
    <p v-if="erreurSuggestions" class="status status-down" role="alert">{{ erreurSuggestions }}</p>
    <p v-else-if="enAttente.length === 0" class="muted">Aucune suggestion en attente.</p>
    <ul v-else data-liste="suggestions">
      <li v-for="suggestion in enAttente" :key="suggestion.id" :data-suggestion="suggestion.id">
        <strong>{{ suggestion.cle }}</strong> : passer en {{ suggestion.statut }}
        <template v-if="suggestion.versionRecommandee"> (version {{ suggestion.versionRecommandee }})</template>
        <p v-if="suggestion.justification">
          <span class="muted" data-mention="ia">{{ MENTION_IA }} :</span> {{ suggestion.justification }}
        </p>
        <span v-if="administrateur" class="actions">
          <button type="button" :disabled="decisionEnCours" data-decision="ACCEPTER" @click="decider(suggestion, 'ACCEPTER')">
            Accepter
          </button>
          <button
            type="button"
            class="secondary"
            :disabled="decisionEnCours"
            data-decision="REJETER"
            @click="decider(suggestion, 'REJETER')"
          >
            Refuser
          </button>
        </span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
[data-tableau='referentiel'] { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
[data-tableau='referentiel'] th, [data-tableau='referentiel'] td {
  text-align: left; padding: 0.3rem 0.5rem; border-bottom: 1px solid var(--border); overflow-wrap: anywhere;
}
</style>
