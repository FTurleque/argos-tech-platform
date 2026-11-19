<script setup lang="ts">
// Impacts d'un signal cité par une alerte, avec leur état de traitement, leur historique et les actions de transition
// (COR-09, COR-10, COR-12 ; décision J5 du porteur du projet, 2026-11-19). Les actions sont celles que le serveur
// renvoie pour l'utilisateur (`transitionsPossibles`) : aucune pour un lecteur. Écarter demande un motif. Les textes
// venus du serveur (projet, composant, motif, commentaire, auteur) sont rendus comme texte, par interpolation.
import { ref, watch } from 'vue'
import { demanderTransition, fetchImpact, fetchImpactsSignal } from '../api/impacts'
import { LONGUEUR_MAX_TEXTE } from '../api/impacts-modele.ts'
import { appliquerTransition, impactAffiche, type ActionTransition, type ImpactAffiche } from './traitement-impact.ts'

const props = defineProps<{ signalId: string }>()

const impacts = ref<ImpactAffiche[]>([])
const erreur = ref('')
const ouvert = ref<string | null>(null)
const enCours = ref<{ impactId: string; action: ActionTransition } | null>(null)
const motif = ref('')
const commentaire = ref('')
const message = ref('')

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('fr-FR') : ''
}

async function charger(): Promise<void> {
  erreur.value = ''
  try {
    const page = await fetchImpactsSignal(props.signalId)
    impacts.value = page.items.map(impactAffiche)
  } catch {
    impacts.value = []
    erreur.value = 'Impacts indisponibles.'
  }
}

async function detailler(id: string): Promise<void> {
  if (ouvert.value === id) {
    ouvert.value = null
    return
  }
  message.value = ''
  enCours.value = null
  try {
    remplacer(impactAffiche(await fetchImpact(id)))
    ouvert.value = id
  } catch {
    message.value = 'Détail de l’impact indisponible.'
  }
}

function remplacer(impact: ImpactAffiche): void {
  impacts.value = impacts.value.map((i) => (i.id === impact.id ? impact : i))
}

function choisir(impactId: string, action: ActionTransition): void {
  enCours.value = { impactId, action }
  motif.value = ''
  commentaire.value = ''
  message.value = ''
}

async function envoyer(): Promise<void> {
  if (!enCours.value) return
  const { impactId, action } = enCours.value
  const issue = await appliquerTransition(impactId, action.vers, motif.value, commentaire.value, demanderTransition)
  if ('erreur' in issue) {
    message.value = issue.erreur
    return
  }
  remplacer(issue.impact)
  enCours.value = null
  message.value = `Impact ${issue.impact.etat}.`
}

watch(() => props.signalId, charger, { immediate: true })
</script>

<template>
  <section class="traitement-impacts" data-traitement-impacts>
    <h2>Impacts et traitement</h2>
    <p v-if="erreur" class="status status-down">{{ erreur }}</p>
    <p v-else-if="impacts.length === 0" class="muted">Aucun impact visible pour ce signal.</p>
    <ul v-else>
      <li v-for="impact in impacts" :key="impact.id" data-impact>
        <p>
          <strong>{{ impact.projet }}</strong> · {{ impact.composant }}
          · niveau <span data-champ="niveau">{{ impact.niveau }}</span>
          · état <span data-champ="etat">{{ impact.etat }}</span>
          <button type="button" class="secondary" :aria-expanded="ouvert === impact.id" @click="detailler(impact.id)">
            {{ ouvert === impact.id ? 'Masquer le détail' : 'Historique et actions' }}
          </button>
        </p>
        <div v-if="ouvert === impact.id" data-detail-impact>
          <h3>Historique</h3>
          <p v-if="impact.historique.length === 0" class="muted">Aucune transition : impact {{ impact.etat }} depuis sa création.</p>
          <ol v-else>
            <li v-for="(ligne, rang) in impact.historique" :key="rang" data-transition>
              {{ date(ligne.date) }} · {{ ligne.de }} → {{ ligne.vers }} · par {{ ligne.auteur }}
              <template v-if="ligne.motif"> · motif : {{ ligne.motif }}</template>
              <template v-if="ligne.commentaire"> · {{ ligne.commentaire }}</template>
            </li>
          </ol>
          <div v-if="impact.actions.length > 0" class="actions" data-actions>
            <button v-for="action in impact.actions" :key="action.vers" type="button" @click="choisir(impact.id, action)">
              {{ action.libelle }}
            </button>
          </div>
          <form v-if="enCours && enCours.impactId === impact.id" @submit.prevent="envoyer">
            <label>
              Motif<template v-if="enCours.action.motifObligatoire"> (obligatoire)</template>
              <textarea v-model="motif" :maxlength="LONGUEUR_MAX_TEXTE" :required="enCours.action.motifObligatoire" rows="2" />
            </label>
            <label>
              Commentaire
              <textarea v-model="commentaire" :maxlength="LONGUEUR_MAX_TEXTE" rows="2" />
            </label>
            <button type="submit">{{ enCours.action.libelle }}</button>
            <button type="button" class="secondary" @click="enCours = null">Annuler</button>
          </form>
        </div>
      </li>
    </ul>
    <p v-if="message" class="muted" role="status">{{ message }}</p>
  </section>
</template>

<style scoped>
ul, ol { padding-left: 1.2rem; }
label { display: block; margin: 0.4rem 0; }
textarea { display: block; width: 100%; }
.actions button, form button { margin-right: 0.5rem; }
</style>
