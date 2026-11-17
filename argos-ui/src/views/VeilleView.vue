<script setup lang="ts">
// « Ma veille » (PRF-02, décision Q-3, AFF-01, AFF-02) : veille de l'utilisateur restituée par le serveur
// comme l'union sans doublon de ses profils actifs et de ses abonnements PERSONAL, sous ses droits, affichée
// dans la mise en page de sa préférence. Vue de la couche contenu : elle lit l'API, transpose la réponse vers
// le modèle commun (`contenu/veille-affichee.ts`, conversion de forme seulement) et la transmet au sélecteur
// central des mises en page. Elle ne filtre, ne dédoublonne ni ne retire aucun élément ni aucune mention
// (arc42 §5.1).
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ApiError } from '../api/client'
import {
  lireAbonnements,
  lireCatalogueProfils,
  lirePreferences,
  lireVeille,
  type Abonnement,
  type PreferencesEffectives,
  type ProfilCatalogue,
  type VeilleProfils,
} from '../api/veille'
import { resoudreMiseEnPage } from '../contenu/resolution'
import { veilleAffichee } from '../contenu/veille-affichee'
import { libelleProfil, messageErreurVeille } from '../contenu/veille-profils'
import MiseEnPage from '../mises-en-page/MiseEnPage.vue'
import { IDENTIFIANTS_MISES_EN_PAGE } from '../mises-en-page/registre'

const veille = ref<VeilleProfils | null>(null)
const catalogue = ref<ProfilCatalogue[]>([])
const abonnements = ref<Abonnement[]>([])
const preferences = ref<PreferencesEffectives | null>(null)
const composeeLe = ref<string | null>(null)
const chargement = ref(false)
const erreur = ref('')

const miseEnPage = computed(() => resoudreMiseEnPage(preferences.value, IDENTIFIANTS_MISES_EN_PAGE))
const affichee = computed(() =>
  veille.value ? veilleAffichee(veille.value, catalogue.value, abonnements.value, composeeLe.value) : null,
)
const profilsActifs = computed(() =>
  (veille.value?.profilsActifs ?? []).map((p) => libelleProfil(p, catalogue.value)).join(', '),
)

let chargementCourant: AbortController | null = null

async function charger(): Promise<void> {
  chargementCourant?.abort()
  const controleur = new AbortController()
  chargementCourant = controleur
  chargement.value = true
  erreur.value = ''
  // Le catalogue et les abonnements ne servent qu'à nommer profils et origines, la préférence qu'à choisir la
  // mise en page : leur échec n'empêche pas d'afficher la veille (identifiants lisibles, mise en page
  // « classique » par défaut, AFF-04).
  const [resultatVeille, resultatCatalogue, resultatAbonnements, resultatPreferences] = await Promise.allSettled([
    lireVeille(controleur.signal),
    lireCatalogueProfils(controleur.signal),
    lireAbonnements(controleur.signal),
    lirePreferences(controleur.signal),
  ])
  if (controleur.signal.aborted) return
  chargement.value = false
  catalogue.value = resultatCatalogue.status === 'fulfilled' ? resultatCatalogue.value : []
  abonnements.value = resultatAbonnements.status === 'fulfilled' ? resultatAbonnements.value : []
  preferences.value = resultatPreferences.status === 'fulfilled' ? resultatPreferences.value : null
  if (resultatVeille.status === 'fulfilled') {
    veille.value = resultatVeille.value
    composeeLe.value = new Date().toISOString()
  } else {
    veille.value = null
    const e = resultatVeille.reason
    erreur.value =
      e instanceof ApiError ? messageErreurVeille('veille', e.status, e.message) : 'Erreur inattendue.'
  }
}

onMounted(charger)
onUnmounted(() => chargementCourant?.abort())
</script>

<template>
  <section class="card" aria-labelledby="titre-veille">
    <h1 id="titre-veille">Ma veille</h1>
    <p class="muted">
      Les éléments de vos profils actifs et de vos abonnements personnels, chacun une seule fois, dans la limite
      de vos droits.
      <RouterLink to="/veille/profils">Choisir mes profils</RouterLink> ·
      <RouterLink to="/affichage">Changer de mise en page</RouterLink>
    </p>
    <p v-if="veille" class="muted">Profils actifs : {{ profilsActifs }}</p>
    <p v-if="miseEnPage.message" class="status status-down" role="status">{{ miseEnPage.message }}</p>
    <p v-if="chargement" class="muted" role="status">Chargement de votre veille…</p>
    <p v-if="erreur" class="status status-down" role="alert">
      {{ erreur }}
      <button type="button" class="secondary" @click="charger">Réessayer</button>
    </p>
    <p v-if="veille && veille.elements.length === 0" class="vide" role="status">
      Aucun élément dans votre veille pour l’instant. Vos profils actifs ne rangent encore aucun élément de vos
      espaces, et aucun abonnement personnel n’en retient.
    </p>
  </section>

  <MiseEnPage
    v-if="affichee && affichee.elements.length > 0"
    :identifiant="miseEnPage.identifiant"
    :veille="affichee"
    :niveau-titre="2"
  />
</template>

<style scoped>
.vide {
  padding: 0.75rem 1rem;
  border: 1px dashed var(--border);
  border-radius: 8px;
}
</style>
