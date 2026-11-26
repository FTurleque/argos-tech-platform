<script setup lang="ts">
// Vue du profil de veille « artiste » (ART-07 ; ART-02, ART-03, ART-05, ART-06) : choix des disciplines et
// territoires suivis, opportunités rapprochées, résumé d'une opportunité, sources vérifiées et rubriques de la veille.
// Adresse d'une opportunité (liens des rappels) : `/#/profils/artiste/opportunites/{id}` ; elle est mise en avant,
// avec son résumé. Le serveur valide les suivis, rapproche les opportunités et compose le résumé, sans avis
// d'éligibilité ; titres, critères et liens viennent des sources : rendus comme texte, liens filtrés par `urlSure`.
// Le registre des sources n'est lisible que par l'administrateur plateforme.
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  enregistrerSuivisArtiste,
  fetchListesArtiste,
  fetchOpportunitesArtiste,
  fetchResumeOpportunite,
  fetchRubriquesVeille,
  fetchSourcesArtiste,
  fetchSuivisArtiste,
  type ListesArtiste,
  type OpportunitesArtiste,
  type ResumeOpportunite,
  type RubriqueVeille,
  type SourceProfil,
} from '../api/artiste'
import { messageEchec } from '../contenu/alimentation'
import { urlSure } from '../contenu/liens'
import { etatSession } from '../session/session'

/** État d'une source dont les conditions d'usage n'ont pas encore été vérifiées : elle n'est pas collectée. */
const SOURCE_A_VERIFIER = 'CONDITIONS_A_VERIFIER'
const PROFIL = 'artiste'

const route = useRoute()
const opportuniteId = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))

const listes = ref<ListesArtiste | undefined>(undefined)
const disciplines = ref<string[]>([])
const territoires = ref<string[]>([])
const erreurSuivis = ref('')
const issueSuivis = ref('')
const enregistrement = ref(false)
const opportunites = ref<OpportunitesArtiste | undefined>(undefined)
const erreurOpportunites = ref('')
const resume = ref<ResumeOpportunite | undefined>(undefined)
const erreurResume = ref('')
const sources = ref<SourceProfil[]>([])
const rubriques = ref<RubriqueVeille[]>([])
let annulation = new AbortController()

const sourcesVerifiees = computed(() => sources.value.filter((source) => source.etat !== SOURCE_A_VERIFIER))
const misEnAvant = computed(() => opportunites.value?.items.find((opportunite) => opportunite.id === opportuniteId.value))
const autres = computed(() => opportunites.value?.items.filter((opportunite) => opportunite.id !== opportuniteId.value) ?? [])

function libelle(valeurs: readonly { identifiant: string; libelle: string }[] | undefined, identifiant: string): string {
  return valeurs?.find((valeur) => valeur.identifiant === identifiant)?.libelle ?? identifiant
}

function dateLimite(date: string | null, aConfirmer: boolean): string {
  return aConfirmer || !date ? 'date limite à confirmer' : `date limite le ${date}`
}

async function chargerOpportunites(): Promise<void> {
  erreurOpportunites.value = ''
  try {
    opportunites.value = await fetchOpportunitesArtiste(annulation.signal)
  } catch (echec) {
    if (!annulation.signal.aborted) erreurOpportunites.value = messageEchec(echec)
  }
}

async function chargerSuivis(): Promise<void> {
  erreurSuivis.value = ''
  try {
    const [listesLues, suivis] = await Promise.all([fetchListesArtiste(annulation.signal), fetchSuivisArtiste(annulation.signal)])
    listes.value = listesLues
    disciplines.value = [...suivis.disciplines]
    territoires.value = [...suivis.territoires]
  } catch (echec) {
    if (!annulation.signal.aborted) erreurSuivis.value = messageEchec(echec)
  }
}

async function chargerRegistres(): Promise<void> {
  const [rubriquesLues, sourcesLues] = await Promise.allSettled([
    fetchRubriquesVeille(annulation.signal),
    etatSession.compte?.adminPlateforme ? fetchSourcesArtiste(annulation.signal) : Promise.resolve([]),
  ])
  rubriques.value = rubriquesLues.status === 'fulfilled' ? rubriquesLues.value : []
  sources.value = sourcesLues.status === 'fulfilled' ? sourcesLues.value : []
}

async function chargerResume(): Promise<void> {
  resume.value = undefined
  erreurResume.value = ''
  const id = opportuniteId.value
  if (id === undefined) return
  try {
    resume.value = await fetchResumeOpportunite(id, annulation.signal)
  } catch (echec) {
    if (!annulation.signal.aborted) erreurResume.value = messageEchec(echec)
  }
}

async function enregistrer(): Promise<void> {
  if (enregistrement.value) return
  enregistrement.value = true
  erreurSuivis.value = ''
  issueSuivis.value = ''
  try {
    const suivis = await enregistrerSuivisArtiste({ disciplines: disciplines.value, territoires: territoires.value })
    disciplines.value = [...suivis.disciplines]
    territoires.value = [...suivis.territoires]
    issueSuivis.value = 'Suivis enregistrés.'
    await chargerOpportunites()
  } catch (echec) {
    erreurSuivis.value = messageEchec(echec)
  } finally {
    enregistrement.value = false
  }
}

onMounted(() => {
  void chargerSuivis()
  void chargerOpportunites()
  void chargerRegistres()
})
watch(opportuniteId, chargerResume, { immediate: true })
onBeforeUnmount(() => annulation.abort())
</script>

<template>
  <section class="card" aria-labelledby="titre-profil-artiste">
    <h1 id="titre-profil-artiste">Profil artiste</h1>
    <p class="muted">
      Appels à projets, résidences, aides et concours qui correspondent à vos disciplines et territoires suivis. Argos ne
      se prononce pas sur votre éligibilité : le texte officiel de l’organisme fait foi.
    </p>
  </section>

  <section v-if="opportuniteId" class="card mise-en-avant" aria-labelledby="titre-opportunite" data-opportunite-en-avant>
    <h2 id="titre-opportunite">{{ misEnAvant?.titre ?? resume?.titre ?? 'Opportunité' }}</h2>
    <p v-if="misEnAvant" class="muted">
      {{ misEnAvant.organisme }} · {{ dateLimite(misEnAvant.dateLimite, misEnAvant.dateAConfirmer) }}
    </p>
    <p v-if="misEnAvant?.explication" class="muted">{{ misEnAvant.explication }}</p>
    <p v-if="erreurResume" class="status status-down" role="alert" data-erreur="resume">{{ erreurResume }}</p>
    <div v-else-if="resume" data-resume>
      <h3>Résumé</h3>
      <p v-if="resume.mentionIa" class="muted" data-mention="ia">{{ resume.mentionIa }}</p>
      <p class="muted">Critères tels qu’énoncés par {{ resume.organisme ?? 'l’organisme' }} :</p>
      <blockquote>{{ resume.criteres }}</blockquote>
      <p v-if="resume.criteresTronques" class="muted">Critères tronqués : le texte officiel fait foi.</p>
      <p class="muted">Date limite : {{ resume.dateLimite }}</p>
      <p v-if="urlSure(resume.texteOfficiel)">
        <a :href="urlSure(resume.texteOfficiel) ?? undefined" target="_blank" rel="noopener noreferrer">Texte officiel</a>
      </p>
      <p class="muted" data-mention="eligibilite">{{ resume.mention }}</p>
    </div>
    <p><RouterLink to="/profils/artiste">← Toutes les opportunités</RouterLink></p>
  </section>

  <section class="card" aria-labelledby="titre-suivis" :aria-busy="enregistrement ? 'true' : undefined">
    <h2 id="titre-suivis">Mes suivis</h2>
    <p v-if="erreurSuivis" class="status status-down" role="alert" data-erreur="suivis">
      {{ erreurSuivis }} <RouterLink to="/veille/profils">Choisir mes profils</RouterLink>
    </p>
    <form v-if="listes" novalidate data-formulaire="suivis" @submit.prevent="enregistrer">
      <fieldset>
        <legend>Disciplines</legend>
        <label v-for="valeur in listes.disciplines" :key="valeur.identifiant" class="choix">
          <input v-model="disciplines" type="checkbox" name="discipline" :value="valeur.identifiant" />
          {{ valeur.libelle }}
        </label>
      </fieldset>
      <fieldset>
        <legend>Territoires</legend>
        <label v-for="valeur in listes.territoires" :key="valeur.identifiant" class="choix">
          <input v-model="territoires" type="checkbox" name="territoire" :value="valeur.identifiant" />
          {{ valeur.libelle }}
        </label>
      </fieldset>
      <button type="submit" :disabled="enregistrement">Enregistrer mes suivis</button>
    </form>
    <output class="muted" aria-live="polite" data-issue="suivis">{{ issueSuivis }}</output>
  </section>

  <section class="card" aria-labelledby="titre-opportunites">
    <h2 id="titre-opportunites">Opportunités</h2>
    <p v-if="erreurOpportunites" class="status status-down" role="alert" data-erreur="opportunites">{{ erreurOpportunites }}</p>
    <output v-else-if="opportunites?.suivisRequis" class="status" data-suivis-requis>{{ opportunites.message }}</output>
    <template v-else-if="opportunites">
      <p v-if="opportunites.items.length === 0" class="muted">Aucune opportunité ouverte pour vos suivis.</p>
      <ul data-liste="opportunites">
        <li v-if="misEnAvant" class="actuelle" aria-current="true" :data-opportunite="misEnAvant.id">
          <strong>{{ misEnAvant.titre }}</strong> <span class="muted">(affichée ci-dessus)</span>
        </li>
        <li v-for="opportunite in autres" :key="opportunite.id" :data-opportunite="opportunite.id">
          <RouterLink :to="`/profils/artiste/opportunites/${opportunite.id}`">{{ opportunite.titre ?? 'Sans titre' }}</RouterLink>
          <span class="muted">
            · {{ opportunite.organisme }} · {{ dateLimite(opportunite.dateLimite, opportunite.dateAConfirmer) }}
            <template v-if="opportunite.disciplines.length">
              · {{ opportunite.disciplines.map((valeur) => libelle(listes?.disciplines, valeur)).join(', ') }}
            </template>
          </span>
          <a
            v-if="urlSure(opportunite.lienOfficiel)"
            :href="urlSure(opportunite.lienOfficiel) ?? undefined"
            target="_blank"
            rel="noopener noreferrer"
          >
            Texte officiel
          </a>
        </li>
      </ul>
    </template>
  </section>

  <section class="card" aria-labelledby="titre-rubriques">
    <h2 id="titre-rubriques">Rubriques de ma veille</h2>
    <p v-if="rubriques.length === 0" class="muted">Aucune rubrique.</p>
    <ul v-else data-liste="rubriques">
      <li v-for="rubrique in rubriques" :key="`${rubrique.profil}-${rubrique.identifiant}`">
        {{ rubrique.libelle }}<span v-if="rubrique.profil !== PROFIL" class="muted"> ({{ rubrique.profil }})</span>
      </li>
    </ul>
  </section>

  <section v-if="sourcesVerifiees.length > 0" class="card" aria-labelledby="titre-sources">
    <h2 id="titre-sources">Sources vérifiées</h2>
    <ul data-liste="sources">
      <li v-for="source in sourcesVerifiees" :key="source.id">
        {{ source.libelle ?? source.adresse }}
        <span class="muted"> · licence : {{ source.licence ?? 'non précisée' }}</span>
        <a
          v-if="urlSure(source.conditionsUrl)"
          :href="urlSure(source.conditionsUrl) ?? undefined"
          target="_blank"
          rel="noopener noreferrer"
        >
          Conditions d’usage
        </a>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.mise-en-avant { border-left: 4px solid var(--teal); }
.mise-en-avant h3 { font-size: 1rem; margin: 0.75rem 0 0.25rem; color: var(--navy); }
blockquote {
  margin: 0.25rem 0; padding-left: 0.75rem; border-left: 3px solid var(--border); white-space: pre-line; overflow-wrap: anywhere;
}
fieldset { border: 1px solid var(--border); border-radius: 8px; margin-bottom: 0.75rem; display: flex; flex-wrap: wrap; gap: 0.5rem 1rem; }
.actuelle { font-weight: 600; }
[data-liste] { overflow-wrap: anywhere; }
</style>
