<script setup lang="ts">
// Recherche dans la veille de l'utilisateur et vues enregistrées (RCH-01 à RCH-04), dans le portail
// web comme dans le client desktop. L'IHM ne fait que saisir, appeler l'API et afficher : la
// validation, les droits et la sémantique des filtres restent dans argos-server (arc42 §5.1).
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import {
  enregistrerVue,
  lireVue,
  listerVues,
  rechercher,
  resultatsVue,
  supprimerVue,
  type ResultatsRecherche,
  type VueEnregistree,
} from '../api/recherche'
import {
  GRAVITES,
  PERIODES,
  TAILLE_PAGE,
  TYPES_CONTENU,
  annonceResultats,
  criteresDuFormulaire,
  estUuid,
  formulaireDesCriteres,
  formulaireVide,
  libelleGravite,
  libellePeriode,
  libelleType,
  messageErreur,
  nombrePages,
  projetsConnus,
  type CriteresRecherche,
  type FormulaireRecherche,
  type ProjetConnu,
} from '../api/recherche-criteres'
import { urlSure } from '../contenu/liens'

type Source = { nature: 'recherche'; criteres: CriteresRecherche } | { nature: 'vue'; id: string }

const route = useRoute()
const router = useRouter()
const idVue = computed(() => (typeof route.params.id === 'string' ? route.params.id : null))

const formulaire = ref<FormulaireRecherche>(formulaireVide())
const projetSaisi = ref('')
const erreurProjet = ref('')
const projets = ref<ProjetConnu[]>([])

const resultats = ref<ResultatsRecherche | null>(null)
const source = ref<Source | null>(null)
const chargement = ref(false)
const erreur = ref('')
const annonce = ref('')
const titreResultats = ref<HTMLElement | null>(null)

const vues = ref<VueEnregistree[]>([])
const erreurVues = ref('')
const vueCourante = ref<VueEnregistree | null>(null)
const nomVue = ref('')
const erreurEnregistrement = ref('')
const annonceVues = ref('')
/** Vue dont la suppression attend une confirmation dans la page. */
const vueASupprimer = ref<VueEnregistree | null>(null)

/**
 * Chargement en cours : un nouveau chargement (recherche, page, vue rappelée) annule le précédent,
 * dont la réponse tardive ne peut plus écraser l'écran.
 */
let chargementCourant: AbortController | null = null

function nouveauChargement(): AbortSignal {
  chargementCourant?.abort()
  chargementCourant = new AbortController()
  return chargementCourant.signal
}

function estAnnule(echec: unknown): boolean {
  return echec instanceof ApiError && echec.motif === 'ANNULE'
}

const pages = computed(() => (resultats.value ? nombrePages(resultats.value.total, resultats.value.taille) : 0))
const optionsProjets = computed(() =>
  projetsConnus(projets.value, formulaire.value.projets.map((projetId) => ({ projetId, projet: null }))),
)

function texteErreur(operation: Parameters<typeof messageErreur>[0], echec: unknown): string {
  if (echec instanceof ApiError && echec.motif === 'DELAI_DEPASSE') {
    return "Le serveur ARGOS n'a pas répondu à temps. Précisez les mots ou les filtres, puis relancez."
  }
  if (echec instanceof ApiError) return messageErreur(operation, echec.status, echec.message)
  return 'Erreur inattendue.'
}

function date(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString('fr-FR') : ''
}

async function afficher(
  nouvelle: Source,
  page: number,
  deplacerFocus: boolean,
  signal: AbortSignal = nouveauChargement(),
): Promise<void> {
  chargement.value = true
  erreur.value = ''
  annonce.value = 'Recherche en cours…'
  try {
    const reponse =
      nouvelle.nature === 'vue'
        ? await resultatsVue(nouvelle.id, page, TAILLE_PAGE, signal)
        : await rechercher(nouvelle.criteres, page, TAILLE_PAGE, signal)
    if (signal.aborted) return
    resultats.value = reponse
    source.value = nouvelle
    projets.value = projetsConnus(projets.value, reponse.elements.flatMap((element) => element.projets))
    annonce.value = annonceResultats(reponse.total, reponse.page, reponse.taille, reponse.totalPlafonne)
  } catch (echec) {
    if (signal.aborted || estAnnule(echec)) return
    resultats.value = null
    erreur.value = texteErreur(nouvelle.nature === 'vue' ? 'vue' : 'recherche', echec)
    annonce.value = ''
  } finally {
    if (!signal.aborted) chargement.value = false
  }
  if (signal.aborted) return
  if (deplacerFocus) {
    await nextTick()
    titreResultats.value?.focus()
  }
}

async function soumettre(): Promise<void> {
  const criteres = criteresDuFormulaire(formulaire.value)
  if (idVue.value) {
    // Une recherche modifiée n'est plus la vue rappelée.
    await router.push('/recherche')
  }
  await afficher({ nature: 'recherche', criteres }, 0, true)
}

function reinitialiser(): void {
  formulaire.value = formulaireVide()
  projetSaisi.value = ''
  erreurProjet.value = ''
}

function changerPage(page: number): void {
  if (source.value) void afficher(source.value, page, true)
}

function ajouterProjet(): void {
  const id = projetSaisi.value.trim()
  if (!estUuid(id)) {
    erreurProjet.value = 'Identifiant de projet attendu : un UUID de 36 caractères.'
    return
  }
  erreurProjet.value = ''
  if (!formulaire.value.projets.includes(id)) formulaire.value.projets.push(id)
  projetSaisi.value = ''
}

function retirerProfil(profil: string): void {
  formulaire.value.profils = formulaire.value.profils.filter((actif) => actif !== profil)
}

async function chargerVues(): Promise<void> {
  erreurVues.value = ''
  try {
    vues.value = await listerVues()
  } catch (echec) {
    erreurVues.value = texteErreur('liste', echec)
  }
}

async function enregistrer(): Promise<void> {
  erreurEnregistrement.value = ''
  annonceVues.value = ''
  if (nomVue.value.trim() === '') {
    erreurEnregistrement.value = 'Donnez un nom à la vue.'
    return
  }
  try {
    const vue = await enregistrerVue(nomVue.value, criteresDuFormulaire(formulaire.value))
    nomVue.value = ''
    annonceVues.value = `Vue « ${vue.nom} » enregistrée.`
    await chargerVues()
    await router.push(`/recherche/vues/${vue.id}`)
  } catch (echec) {
    erreurEnregistrement.value = texteErreur('enregistrement', echec)
  }
}

/** Vide les résultats affichés (vue supprimée, vue introuvable) et abandonne tout chargement. */
function viderResultats(): void {
  chargementCourant?.abort()
  chargementCourant = null
  chargement.value = false
  resultats.value = null
  source.value = null
  annonce.value = ''
}

function demanderSuppression(vue: VueEnregistree): void {
  erreurVues.value = ''
  annonceVues.value = ''
  vueASupprimer.value = vue
}

async function confirmerSuppression(): Promise<void> {
  const vue = vueASupprimer.value
  vueASupprimer.value = null
  if (!vue) return
  try {
    await supprimerVue(vue.id)
    annonceVues.value = `Vue « ${vue.nom} » supprimée.`
    const affichee = idVue.value === vue.id || (source.value?.nature === 'vue' && source.value.id === vue.id)
    if (affichee) viderResultats()
    await chargerVues()
    if (idVue.value === vue.id) await router.push('/recherche')
  } catch (echec) {
    erreurVues.value = texteErreur('suppression', echec)
  }
}

async function rappeler(id: string): Promise<void> {
  vueCourante.value = null
  if (!estUuid(id)) {
    viderResultats()
    erreur.value = 'Vue introuvable.'
    return
  }
  const signal = nouveauChargement()
  try {
    const vue = await lireVue(id, signal)
    if (signal.aborted) return
    vueCourante.value = vue
    formulaire.value = formulaireDesCriteres(vue.criteres)
  } catch (echec) {
    if (signal.aborted || estAnnule(echec)) return
    resultats.value = null
    erreur.value = texteErreur('vue', echec)
    return
  }
  await afficher({ nature: 'vue', id }, 0, false, signal)
}

watch(
  idVue,
  (id) => {
    if (id) void rappeler(id)
    else vueCourante.value = null
  },
  { immediate: true },
)

onMounted(chargerVues)
</script>

<template>
  <section class="card recherche" aria-labelledby="titre-recherche">
    <h1 id="titre-recherche">Recherche</h1>
    <p v-if="vueCourante" class="muted">
      Vue rappelée : <strong>{{ vueCourante.nom }}</strong> — ses mots et ses filtres sont appliqués sous vos droits actuels.
    </p>

    <form class="formulaire" role="search" aria-label="Rechercher dans la veille" @submit.prevent="soumettre">
      <div class="champ">
        <label for="recherche-texte">Mots recherchés</label>
        <input
          id="recherche-texte"
          v-model="formulaire.texte"
          type="search"
          maxlength="500"
          autocomplete="off"
          aria-describedby="aide-texte"
        />
        <p id="aide-texte" class="muted">Mots, « "expression exacte" », <code>or</code>, <code>-exclusion</code>.</p>
      </div>

      <div class="filtres">
        <div class="champ">
          <label for="recherche-periode">Période</label>
          <select id="recherche-periode" v-model="formulaire.horizonJours">
            <option v-for="periode in PERIODES" :key="String(periode.jours)" :value="periode.jours">{{ periode.libelle }}</option>
            <option
              v-if="formulaire.horizonJours !== null && !PERIODES.some((periode) => periode.jours === formulaire.horizonJours)"
              :value="formulaire.horizonJours"
            >
              {{ libellePeriode(formulaire.horizonJours) }}
            </option>
          </select>
        </div>

        <div class="champ">
          <label for="recherche-gravite">Gravité minimale</label>
          <select id="recherche-gravite" v-model="formulaire.gravite">
            <option value="">Toutes</option>
            <option v-for="gravite in GRAVITES" :key="gravite.code" :value="gravite.code">{{ gravite.libelle }}</option>
          </select>
        </div>

        <div class="champ">
          <label for="recherche-technologies">Technologies</label>
          <input
            id="recherche-technologies"
            v-model="formulaire.technologies"
            type="text"
            autocomplete="off"
            aria-describedby="aide-technologies"
          />
          <p id="aide-technologies" class="muted">Séparées par des virgules (Oracle, java, npm…).</p>
        </div>
      </div>

      <fieldset class="groupe">
        <legend>Type de contenu</legend>
        <label v-for="typeContenu in TYPES_CONTENU" :key="typeContenu.code" class="case">
          <input v-model="formulaire.types" type="checkbox" :value="typeContenu.code" />
          {{ typeContenu.libelle }}
        </label>
      </fieldset>

      <fieldset class="groupe">
        <legend>Projets</legend>
        <p v-if="optionsProjets.length === 0" class="muted">
          Les projets cités par les résultats s'ajoutent ici ; vous pouvez aussi saisir l'identifiant d'un projet.
        </p>
        <label v-for="projet in optionsProjets" :key="projet.projetId" class="case">
          <input v-model="formulaire.projets" type="checkbox" :value="projet.projetId" />
          {{ projet.projet ?? projet.projetId }}
        </label>
        <div class="ajout-projet">
          <label for="recherche-projet-id">Identifiant de projet</label>
          <input
            id="recherche-projet-id"
            v-model="projetSaisi"
            type="text"
            autocomplete="off"
            :aria-invalid="erreurProjet !== ''"
            :aria-describedby="erreurProjet ? 'erreur-projet' : undefined"
            @keydown.enter.prevent="ajouterProjet"
          />
          <button type="button" class="secondary" @click="ajouterProjet">Ajouter le projet</button>
          <p v-if="erreurProjet" id="erreur-projet" class="erreur" role="alert">{{ erreurProjet }}</p>
        </div>
      </fieldset>

      <div v-if="formulaire.profils.length > 0" class="groupe">
        <p class="muted">Profils de veille de la vue :</p>
        <ul class="profils">
          <li v-for="profil in formulaire.profils" :key="profil">
            {{ profil }}
            <button type="button" class="secondary" :aria-label="`Retirer le profil ${profil}`" @click="retirerProfil(profil)">
              Retirer
            </button>
          </li>
        </ul>
      </div>

      <div class="actions">
        <button type="submit" :disabled="chargement">Rechercher</button>
        <button type="button" class="secondary" @click="reinitialiser">Effacer les filtres</button>
      </div>
    </form>

    <p class="sr-only" role="status" aria-live="polite">{{ annonce }}</p>
  </section>

  <section class="card" aria-labelledby="titre-resultats">
    <h2 id="titre-resultats" ref="titreResultats" tabindex="-1">Résultats</h2>
    <p v-if="erreur" class="status status-down" role="alert">{{ erreur }}</p>
    <p v-else-if="chargement" class="muted">Recherche en cours…</p>
    <p v-else-if="!resultats" class="muted">Lancez une recherche ou rappelez une vue enregistrée.</p>
    <p v-else-if="resultats.elements.length === 0" class="muted">
      {{ resultats.message ?? 'Aucun élément ne correspond à votre recherche.' }}
    </p>
    <template v-else>
      <p class="muted">{{ annonceResultats(resultats.total, resultats.page, resultats.taille, resultats.totalPlafonne) }}</p>
      <ol class="resultats" :start="resultats.page * resultats.taille + 1">
        <li v-for="element in resultats.elements" :key="`${element.type}-${element.id}`" class="resultat">
          <h3>
            <a
              v-if="urlSure(element.lien)"
              :href="urlSure(element.lien) ?? undefined"
              target="_blank"
              rel="noopener noreferrer"
            >{{ element.titre ?? element.identifiant ?? 'Sans titre' }}<span class="sr-only"> (source externe, nouvelle fenêtre)</span></a>
            <span v-else>{{ element.titre ?? element.identifiant ?? 'Sans titre' }}</span>
          </h3>
          <p class="muted">
            {{ libelleType(element.type) }}
            <template v-if="element.source"> · {{ element.source }}</template>
            <template v-if="element.identifiant"> {{ element.identifiant }}</template>
            <template v-if="element.gravite"> · gravité {{ libelleGravite(element.gravite) }}</template>
            <template v-if="element.date"> · {{ date(element.date) }}</template>
          </p>
          <p v-if="element.extrait">{{ element.extrait }}</p>
          <p v-if="element.projets.length > 0" class="muted">
            Projets : {{ element.projets.map((projet) => projet.projet ?? projet.projetId).join(', ') }}
          </p>
        </li>
      </ol>
      <nav v-if="pages > 1" class="pagination" aria-label="Pagination des résultats">
        <button
          type="button"
          class="secondary"
          :disabled="chargement || resultats.page === 0"
          @click="changerPage(resultats.page - 1)"
        >
          Page précédente
        </button>
        <span>Page {{ resultats.page + 1 }} sur {{ pages }}</span>
        <button
          type="button"
          class="secondary"
          :disabled="chargement || resultats.page + 1 >= pages"
          @click="changerPage(resultats.page + 1)"
        >
          Page suivante
        </button>
      </nav>
    </template>
  </section>

  <section class="card" aria-labelledby="titre-vues">
    <h2 id="titre-vues">Mes vues</h2>
    <form class="enregistrement" @submit.prevent="enregistrer">
      <label for="vue-nom">Nom de la vue</label>
      <input
        id="vue-nom"
        v-model="nomVue"
        type="text"
        maxlength="120"
        required
        autocomplete="off"
        :aria-invalid="erreurEnregistrement !== ''"
        :aria-describedby="erreurEnregistrement ? 'erreur-vue' : 'aide-vue'"
      />
      <button type="submit">Enregistrer la recherche</button>
      <p id="aide-vue" class="muted">Enregistre les mots et les filtres saisis ci-dessus.</p>
      <p v-if="erreurEnregistrement" id="erreur-vue" class="erreur" role="alert">{{ erreurEnregistrement }}</p>
    </form>
    <p class="sr-only" role="status" aria-live="polite">{{ annonceVues }}</p>
    <p v-if="erreurVues" class="status status-down" role="alert">{{ erreurVues }}</p>
    <p v-else-if="vues.length === 0" class="muted">Aucune vue enregistrée.</p>
    <ul v-else class="vues">
      <li v-for="vue in vues" :key="vue.id">
        <RouterLink :to="`/recherche/vues/${vue.id}`" :aria-current="vue.id === idVue ? 'page' : undefined">
          {{ vue.nom }}
        </RouterLink>
        <span class="muted"> · {{ libellePeriode(vue.criteres?.horizonJours) }}</span>
        <button type="button" class="secondary" :aria-label="`Supprimer la vue ${vue.nom}`" @click="demanderSuppression(vue)">
          Supprimer
        </button>
      </li>
    </ul>
    <div v-if="vueASupprimer" class="confirmation" role="alertdialog" aria-labelledby="confirmation-suppression">
      <p id="confirmation-suppression">Supprimer la vue « {{ vueASupprimer.nom }} » ? Cette action est définitive.</p>
      <button type="button" @click="confirmerSuppression">Supprimer définitivement</button>
      <button type="button" class="secondary" @click="vueASupprimer = null">Annuler</button>
    </div>
  </section>
</template>

<style scoped>
.formulaire { display: grid; gap: 0.75rem; }
.champ { display: grid; gap: 0.25rem; }
.champ p { margin: 0; }
.filtres { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.75rem; }
.groupe { border: 1px solid var(--border); border-radius: 8px; padding: 0.5rem 0.75rem; margin: 0; }
.case { display: inline-flex; align-items: center; gap: 0.35rem; margin-right: 1rem; }
.ajout-projet { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin-top: 0.5rem; }
.profils, .vues { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.35rem; }
input[type='search'], input[type='text'], select { font: inherit; padding: 0.35rem 0.5rem; border: 1px solid var(--border); border-radius: 6px; }
input:focus-visible, select:focus-visible, a:focus-visible, h2:focus-visible { outline: 2px solid var(--navy); outline-offset: 2px; }
.resultats { padding-left: 1.25rem; display: grid; gap: 0.75rem; }
.resultat h3 { margin: 0; font-size: 1rem; }
.resultat p { margin: 0.2rem 0; }
.pagination { display: flex; align-items: center; gap: 0.75rem; margin-top: 1rem; }
.enregistrement { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem; }
.enregistrement p { flex-basis: 100%; margin: 0; }
.erreur { color: var(--ko); margin: 0; }
.confirmation { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin-top: 0.75rem; }
.confirmation p { flex-basis: 100%; margin: 0; }
button:disabled { opacity: 0.6; cursor: default; }
</style>
