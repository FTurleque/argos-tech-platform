<script setup lang="ts">
// « Ma veille » (PRF-02, décision Q-3) : veille de l'utilisateur restituée par le serveur comme l'union
// sans doublon de ses profils actifs et de ses abonnements PERSONAL, sous ses droits. Vue de la couche
// contenu, affichée en liste simple et accessible ; elle ne filtre, ne dédoublonne ni ne retire aucun
// élément ni aucune mention (arc42 §5.1, AFF-02).
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ApiError } from '../api/client'
import {
  lireAbonnements,
  lireCatalogueProfils,
  lireVeille,
  type Abonnement,
  type ProfilCatalogue,
  type VeilleProfils,
} from '../api/veille'
import { urlSure } from '../contenu/liens'
import {
  dateLisible,
  grouperParRubrique,
  libelleGravite,
  libelleProfil,
  libelleType,
  messageErreurVeille,
  originesElement,
} from '../contenu/veille-profils'

const veille = ref<VeilleProfils | null>(null)
const catalogue = ref<ProfilCatalogue[]>([])
const abonnements = ref<Abonnement[]>([])
const chargement = ref(false)
const erreur = ref('')

const groupes = computed(() => grouperParRubrique(veille.value?.elements ?? [], catalogue.value))
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
  // Le catalogue et les abonnements ne servent qu'à nommer profils et origines : leur échec n'empêche pas
  // d'afficher la veille, dont les identifiants restent lisibles.
  const [resultatVeille, resultatCatalogue, resultatAbonnements] = await Promise.allSettled([
    lireVeille(controleur.signal),
    lireCatalogueProfils(controleur.signal),
    lireAbonnements(controleur.signal),
  ])
  if (controleur.signal.aborted) return
  chargement.value = false
  catalogue.value = resultatCatalogue.status === 'fulfilled' ? resultatCatalogue.value : []
  abonnements.value = resultatAbonnements.status === 'fulfilled' ? resultatAbonnements.value : []
  if (resultatVeille.status === 'fulfilled') {
    veille.value = resultatVeille.value
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
      <RouterLink to="/veille/profils">Choisir mes profils</RouterLink>
    </p>
    <p v-if="veille" class="muted">Profils actifs : {{ profilsActifs }}</p>
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

  <section v-for="groupe in groupes" :key="groupe.cle" class="card" :aria-labelledby="`groupe-${groupe.cle}`">
    <h2 :id="`groupe-${groupe.cle}`">
      {{ groupe.titre }}<span v-if="groupe.profil" class="muted"> — profil {{ groupe.profil }}</span>
      <span class="muted"> ({{ groupe.elements.length }})</span>
    </h2>
    <ul class="elements">
      <li v-for="element in groupe.elements" :key="`${element.type}-${element.id}`" class="element" data-element>
        <h3 class="titre">{{ element.titre ?? element.identifiant ?? element.id }}</h3>
        <p class="meta">
          <span>{{ libelleType(element.type) }}</span>
          <span v-if="element.identifiant"> · {{ element.identifiant }}</span>
          <span v-if="libelleGravite(element.gravite)"> · gravité {{ libelleGravite(element.gravite) }}</span>
        </p>
        <p v-if="element.resume">{{ element.resume }}</p>
        <dl class="details">
          <div>
            <dt>Origine</dt>
            <dd data-origine>{{ originesElement(element, catalogue, abonnements).join(' · ') }}</dd>
          </div>
          <div v-if="element.rubriques.length > 1">
            <dt>Rubriques</dt>
            <dd>{{ element.rubriques.map((r) => r.libelle).join(', ') }}</dd>
          </div>
          <div>
            <dt>Source</dt>
            <dd>
              <a v-if="urlSure(element.lien)" :href="urlSure(element.lien)!" rel="noopener noreferrer" target="_blank">{{
                element.source ?? 'source'
              }}</a>
              <span v-else>{{ element.source ?? 'non précisée' }}</span>
            </dd>
          </div>
          <div v-if="element.date">
            <dt>Publié le</dt>
            <dd><time :datetime="element.date">{{ dateLisible(element.date) }}</time></dd>
          </div>
          <div v-if="element.dateEffet">
            <dt>Entrée en vigueur</dt>
            <dd><time :datetime="element.dateEffet">{{ dateLisible(element.dateEffet) }}</time></dd>
          </div>
          <div v-if="element.projets.length">
            <dt>Projets concernés</dt>
            <dd>{{ element.projets.map((p) => p.projet ?? p.projetId).join(', ') }}</dd>
          </div>
          <div v-if="element.mentionValidation">
            <dt>Validation</dt>
            <dd data-mention="validation">{{ element.mentionValidation }}</dd>
          </div>
        </dl>
        <ul v-if="element.gardeFous.length" class="garde-fous" aria-label="Mentions obligatoires">
          <li v-for="g in element.gardeFous" :key="g">{{ g }}</li>
        </ul>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.elements {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}
.element {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.75rem 1rem;
}
.titre {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
}
.meta {
  margin: 0 0 0.5rem;
  color: var(--muted);
  font-size: 0.9em;
}
.details {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1.25rem;
  margin: 0.5rem 0 0;
  font-size: 0.9em;
}
.details div {
  display: flex;
  gap: 0.35rem;
}
.details dt {
  font-weight: 600;
}
.details dt::after {
  content: ' :';
}
.details dd {
  margin: 0;
}
.garde-fous {
  margin: 0.5rem 0 0;
  padding-left: 1.1rem;
  font-size: 0.85em;
  color: var(--muted);
}
.vide {
  padding: 0.75rem 1rem;
  border: 1px dashed var(--border);
  border-radius: 8px;
}
</style>
