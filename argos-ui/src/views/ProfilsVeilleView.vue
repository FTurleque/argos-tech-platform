<script setup lang="ts">
// Choix des profils de veille actifs (PRF-01) : profils publiés au catalogue, préférences enregistrées
// sur le serveur. Chaque clic envoie l'ensemble complet des profils demandés ; le serveur décide seul de
// sa validité (profils publiés, au moins un profil actif) et l'IHM affiche son refus tel quel (arc42 §5.1).
// La mise en page se choisit dans la vue « Mise en page » (AffichageView).
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '../api/client'
import {
  choisirProfils,
  lireCatalogueProfils,
  lirePreferences,
  type PreferencesEffectives,
  type ProfilCatalogue,
} from '../api/veille'
import { basculerProfil, libelleProfil, messageErreurVeille } from '../contenu/veille-profils'

const catalogue = ref<ProfilCatalogue[]>([])
const preferences = ref<PreferencesEffectives | null>(null)
const erreur = ref('')
const annonce = ref('')
const enCours = ref(false)

const actifs = computed(() => preferences.value?.profilsActifs ?? [])
const indisponibles = computed(() => preferences.value?.profilsIndisponibles ?? [])

function message(operation: 'profils' | 'choix', echec: unknown): string {
  return echec instanceof ApiError ? messageErreurVeille(operation, echec.status, echec.message) : 'Erreur inattendue.'
}

async function charger(): Promise<void> {
  erreur.value = ''
  try {
    const [profils, prefs] = await Promise.all([lireCatalogueProfils(), lirePreferences()])
    catalogue.value = profils
    preferences.value = prefs
  } catch (echec) {
    erreur.value = message('profils', echec)
  }
}

// Pendant l'enregistrement, le bouton reste focalisable (`aria-disabled`, pas `disabled`) : le focus clavier
// ne retombe pas sur la page ; ce garde ignore les clics.
async function basculer(identifiant: string): Promise<void> {
  if (!preferences.value || enCours.value) return
  enCours.value = true
  erreur.value = ''
  annonce.value = ''
  try {
    preferences.value = await choisirProfils(basculerProfil(actifs.value, identifiant))
    annonce.value = `Profils actifs enregistrés : ${actifs.value.map((profil) => libelleProfil(profil, catalogue.value)).join(', ')}.`
  } catch (echec) {
    erreur.value = message('choix', echec)
  } finally {
    enCours.value = false
  }
}

onMounted(charger)
</script>

<template>
  <section class="card" aria-labelledby="titre-profils">
    <h1 id="titre-profils">Vos profils de veille</h1>
    <p class="muted">
      Ils décident de ce qu’Argos surveille pour vous. Vous pouvez en cumuler plusieurs ; ils ne changent pas vos
      droits d’accès. Vos choix sont enregistrés sur le serveur : vous les retrouvez dans le portail web et dans
      l’application desktop.
    </p>
    <p v-if="preferences?.profilsParDefaut" class="muted" role="status">
      Aucun profil choisi : les profils par défaut de la plateforme s’appliquent.
    </p>
    <p v-if="indisponibles.length" class="status status-down" role="status">
      Profils retirés du catalogue, ignorés : {{ indisponibles.join(', ') }}.
    </p>
    <p v-if="erreur" class="status status-down" role="alert">{{ erreur }}</p>
    <p class="sr-only" role="status" aria-live="polite">{{ annonce }}</p>

    <ul v-if="preferences" class="profils">
      <li v-for="profil in catalogue" :key="profil.identifiant">
        <button
          type="button"
          class="profil"
          :class="{ actif: actifs.includes(profil.identifiant) }"
          :aria-pressed="actifs.includes(profil.identifiant)"
          :aria-disabled="enCours"
          @click="basculer(profil.identifiant)"
        >
          <span class="coche" aria-hidden="true">{{ actifs.includes(profil.identifiant) ? '✓' : '' }}</span>
          <span class="texte">
            <span class="nom">{{ profil.libelle }}</span>
            <span class="description">{{ profil.description }}</span>
            <span v-if="profil.rubriques.length" class="rubriques">
              Rubriques : {{ profil.rubriques.map((rubrique) => rubrique.libelle).join(', ') }}
            </span>
          </span>
        </button>
      </li>
    </ul>
    <p class="muted">
      <RouterLink to="/veille">Voir ma veille</RouterLink> ·
      <RouterLink to="/affichage">Choisir la mise en page</RouterLink>
    </p>
  </section>
</template>

<style scoped>
.profils {
  list-style: none;
  margin: 1rem 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}
.profil {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  width: 100%;
  text-align: left;
  padding: 0.85rem 1rem;
  border: 2px solid var(--muted);
  border-radius: 8px;
  background: var(--card);
  color: var(--text);
  font: inherit;
  cursor: pointer;
}
.profil.actif {
  border-color: var(--teal);
  background: var(--teal-soft);
}
.profil[aria-disabled='true'] {
  cursor: progress;
}
.profil:focus-visible {
  outline: 3px solid var(--navy);
  outline-offset: 2px;
}
.coche {
  flex: none;
  width: 1.4rem;
  height: 1.4rem;
  border: 2px solid var(--teal);
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--card);
  background: var(--card);
  font-weight: 700;
}
.profil.actif .coche {
  background: var(--teal);
}
.texte {
  display: grid;
  gap: 0.2rem;
}
.nom {
  font-weight: 600;
}
.description,
.rubriques {
  color: var(--muted);
  font-size: 0.9em;
}
</style>
