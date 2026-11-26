<script setup lang="ts">
// Activité factuelle d'un projet (PIN-01, PIN-02, PIN-05, PIN-06) : compteurs sur 30 jours, jalons avec leur
// dénominateur, fraîcheur des données, collecte immédiate (« Collecter maintenant ») et synthèse produite par l'IA,
// marquée comme telle (PIN-04). Aucun indicateur ne désigne une personne (PIN-03) ; les titres des jalons viennent
// de la forge et sont rendus comme texte.
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchActivite, fetchSyntheseActivite, JOURS_ACTIVITE, type SyntheseActivite, type VueActivite } from '../api/activite'
import { collecterActivite } from '../api/projets'
import { libelleCollecte, messageEchec } from './alimentation'
import { MENTION_IA } from './resume-ia'

const props = defineProps<{ projetId: string }>()

const ETATS_FRAICHEUR: Readonly<Record<string, string>> = {
  JAMAIS_COLLECTE: 'jamais collectée',
  A_JOUR: 'à jour',
  REPORTEE_QUOTA: 'reportée (quota de la forge atteint)',
  ECHEC: 'en échec',
}

const activite = ref<VueActivite | null>(null)
const erreur = ref('')
const issue = ref('')
const erreurCollecte = ref('')
const collecte = ref(false)
const synthese = ref<SyntheseActivite | null>(null)
const erreurSynthese = ref('')
let annulation = new AbortController()

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('fr-FR') : 'jamais'
}

function etatFraicheur(etat: string): string {
  return ETATS_FRAICHEUR[etat] ?? etat
}

async function charger(): Promise<void> {
  annulation.abort()
  annulation = new AbortController()
  const signal = annulation.signal
  erreur.value = ''
  try {
    activite.value = await fetchActivite(props.projetId, JOURS_ACTIVITE, signal)
  } catch (echec) {
    if (signal.aborted) return
    activite.value = null
    erreur.value = messageEchec(echec)
  }
}

async function collecter(): Promise<void> {
  collecte.value = true
  issue.value = ''
  erreurCollecte.value = ''
  try {
    issue.value = libelleCollecte((await collecterActivite(props.projetId)).issue)
    await charger()
  } catch (echec) {
    erreurCollecte.value = messageEchec(echec)
  } finally {
    collecte.value = false
  }
}

async function demanderSynthese(): Promise<void> {
  erreurSynthese.value = ''
  try {
    synthese.value = await fetchSyntheseActivite(props.projetId)
  } catch (echec) {
    erreurSynthese.value = messageEchec(echec)
  }
}

watch(
  () => props.projetId,
  () => {
    issue.value = ''
    synthese.value = null
    void charger()
  },
  { immediate: true },
)
onBeforeUnmount(() => annulation.abort())
</script>

<template>
  <section aria-labelledby="titre-activite" class="activite">
    <h2 id="titre-activite">Activité</h2>
    <p v-if="erreur" class="status status-down" role="alert" data-erreur="activite">{{ erreur }}</p>
    <template v-if="activite">
      <p class="muted" data-fraicheur>
        Collecte {{ etatFraicheur(activite.fraicheur.etat) }} · dernière mise à jour :
        {{ date(activite.fraicheur.derniereMiseAJour) }}<template v-if="activite.fraicheur.motif">
          · {{ activite.fraicheur.motif }}</template
        ><template v-if="!activite.fraicheur.complet"> · collecte partielle</template>
      </p>
      <dl class="compteurs">
        <dt>Tickets ouverts / fermés</dt>
        <dd>{{ activite.compteurs.ticketsOuverts }} / {{ activite.compteurs.ticketsFermes }}</dd>
        <dt>Fusions ouvertes / fusionnées</dt>
        <dd>{{ activite.compteurs.fusionsOuvertes }} / {{ activite.compteurs.fusionsFusionnees }}</dd>
        <dt>Pipelines réussis / échoués / autres</dt>
        <dd>
          {{ activite.compteurs.pipelinesReussis }} / {{ activite.compteurs.pipelinesEchoues }} /
          {{ activite.compteurs.pipelinesAutres }}
        </dd>
        <dt>Versions publiées</dt>
        <dd>{{ activite.compteurs.versionsPubliees }}</dd>
      </dl>
      <p class="muted">Sur {{ activite.periode.jours }} jours, du {{ activite.periode.debut }} au {{ activite.periode.fin }}.</p>
      <ul v-if="activite.jalons.length > 0" class="jalons">
        <li v-for="(jalon, rang) in activite.jalons" :key="`${rang}-${jalon.titre}`">
          {{ jalon.titre ?? 'Jalon sans titre' }}
          <span class="muted">
            <template v-if="jalon.avancement"> · {{ jalon.avancement.libelle }}</template>
            <template v-else> · {{ jalon.ticketsFermes }} ticket(s) fermé(s) sur {{ jalon.ticketsTotal }}</template>
            <template v-if="jalon.echeance"> · échéance {{ jalon.echeance }}</template>
          </span>
        </li>
      </ul>
    </template>
    <div class="actions">
      <button type="button" :disabled="collecte" data-action="collecter" @click="collecter">Collecter maintenant</button>
      <button type="button" class="secondary" data-action="synthese" @click="demanderSynthese">Synthèse de l’activité</button>
    </div>
    <p class="muted" role="status" aria-live="polite" data-issue="collecte">{{ issue }}</p>
    <p v-if="erreurCollecte" class="status status-down" role="alert" data-erreur="collecte">{{ erreurCollecte }}</p>
    <p v-if="erreurSynthese" class="status status-down" role="alert">{{ erreurSynthese }}</p>
    <div v-if="synthese" class="synthese">
      <p class="muted" data-mention="ia">{{ synthese.mention || MENTION_IA }}</p>
      <p v-if="synthese.texte">{{ synthese.texte }}</p>
      <p v-else class="muted">Synthèse indisponible ({{ synthese.etat }}).</p>
    </div>
  </section>
</template>

<style scoped>
.compteurs { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem; }
.compteurs dt { font-weight: 600; }
.compteurs dd { margin: 0; }
.jalons { padding-left: 1.2rem; overflow-wrap: anywhere; }
</style>
