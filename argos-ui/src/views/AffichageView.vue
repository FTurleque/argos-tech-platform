<script setup lang="ts">
// Choix de la mise en page (AFF-01, AFF-03, AFF-04). Vue de la couche contenu : elle lit la
// préférence sur le serveur, la confronte aux mises en page connues de l'IHM et transmet des
// modèles de vue à la couche mise en page.
import { computed, onMounted, ref } from 'vue'
import { ApiError, fetchMisesEnPage, type MiseEnPageCatalogue } from '../api/client'
import { STATUT_NON_AUTHENTIFIE } from '../api/statuts-http'
import { choisirMiseEnPage, lirePreferences, type PreferencesEffectives } from '../api/veille'
import { VEILLE_APERCU } from '../contenu/apercu'
import { resoudreMiseEnPage } from '../contenu/resolution'
import MiseEnPage from '../mises-en-page/MiseEnPage.vue'
import { IDENTIFIANTS_MISES_EN_PAGE } from '../mises-en-page/registre'

const preferences = ref<PreferencesEffectives | null>(null)
const catalogue = ref<MiseEnPageCatalogue[]>([])
const erreur = ref('')
const enCours = ref(false)

const resolue = computed(() => resoudreMiseEnPage(preferences.value, IDENTIFIANTS_MISES_EN_PAGE))
const choix = computed(() => catalogue.value.filter((miseEnPage) => IDENTIFIANTS_MISES_EN_PAGE.includes(miseEnPage.identifiant)))

function message(echec: unknown): string {
  if (echec instanceof ApiError && echec.status === STATUT_NON_AUTHENTIFIE) {
    return 'Connectez-vous pour enregistrer votre mise en page. La mise en page « classique » s’applique.'
  }
  return echec instanceof ApiError ? echec.message : 'Erreur inattendue'
}

async function charger(): Promise<void> {
  erreur.value = ''
  try {
    const [prefs, misesEnPage] = await Promise.all([lirePreferences(), fetchMisesEnPage()])
    preferences.value = prefs
    catalogue.value = misesEnPage
  } catch (echec) {
    preferences.value = null
    erreur.value = message(echec)
  }
}

async function choisir(identifiant: string): Promise<void> {
  enCours.value = true
  erreur.value = ''
  try {
    preferences.value = await choisirMiseEnPage(identifiant)
  } catch (echec) {
    erreur.value = message(echec)
  } finally {
    enCours.value = false
  }
}

onMounted(charger)
</script>

<template>
  <section class="card">
    <h1>Mise en page</h1>
    <p class="muted">
      La mise en page change la présentation, jamais le contenu : les mêmes éléments et les mêmes mentions
      s’affichent dans chacune. Votre choix est enregistré sur le serveur et vaut pour le portail web comme pour
      l’application desktop.
    </p>
    <p v-if="resolue.message" class="status status-down" role="status">{{ resolue.message }}</p>
    <p v-if="erreur" class="status status-down" role="alert">{{ erreur }}</p>

    <div v-if="choix.length" class="actions" role="radiogroup" aria-label="Mises en page disponibles">
      <button
        v-for="miseEnPage in choix"
        :key="miseEnPage.identifiant"
        type="button"
        role="radio"
        :aria-checked="miseEnPage.identifiant === resolue.identifiant"
        :class="{ secondary: miseEnPage.identifiant !== resolue.identifiant }"
        :disabled="enCours"
        :title="miseEnPage.description"
        @click="choisir(miseEnPage.identifiant)"
      >
        {{ miseEnPage.libelle }}
      </button>
    </div>
  </section>

  <section class="card" aria-labelledby="titre-apercu">
    <h2 id="titre-apercu">Aperçu — données d’exemple</h2>
    <p class="muted">
      Éléments fictifs, identiques dans les quatre mises en page : l’aperçu montre la forme, pas votre veille.
    </p>
    <MiseEnPage :identifiant="resolue.identifiant" :veille="VEILLE_APERCU" :niveau-titre="3" />
  </section>
</template>
