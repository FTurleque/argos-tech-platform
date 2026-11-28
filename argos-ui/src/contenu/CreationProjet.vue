<script setup lang="ts">
// Création d'un projet dans un espace (INV-12) : choix de l'espace, nom et dépôt facultatif. Seuls les espaces dont
// l'utilisateur est administrateur sont proposés, le serveur refusant la création aux autres rôles ; il valide la
// saisie et son refus est affiché tel quel. Chaque espace mène aussi à sa vue radar.
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchEspaces, type EspaceDeCompte } from '../api/espaces'
import { creerProjet, type ProjetCree } from '../api/projets'
import { depotDeSaisie, messageEchec, saisieDepotVide } from './alimentation'
import SaisieDepot from './SaisieDepot.vue'

const emit = defineEmits<{ cree: [projet: ProjetCree] }>()

const espaces = ref<EspaceDeCompte[]>([])
const erreurEspaces = ref('')
const espaceId = ref('')
const nom = ref('')
const saisie = ref(saisieDepotVide())
const enCours = ref(false)
const erreur = ref('')
const issue = ref('')
let annulation = new AbortController()

const administres = computed(() => espaces.value.filter((espace) => espace.role === 'ADMINISTRATEUR'))

async function chargerEspaces(): Promise<void> {
  annulation.abort()
  annulation = new AbortController()
  erreurEspaces.value = ''
  try {
    espaces.value = await fetchEspaces(annulation.signal)
    if (administres.value.length === 1) espaceId.value = administres.value[0].id
  } catch (echec) {
    if (!annulation.signal.aborted) erreurEspaces.value = messageEchec(echec)
  }
}

async function creer(): Promise<void> {
  erreur.value = ''
  issue.value = ''
  if (!espaceId.value) erreur.value = 'Choisissez l’espace du projet.'
  else if (!nom.value.trim()) erreur.value = 'Saisissez le nom du projet.'
  if (erreur.value) return
  enCours.value = true
  try {
    const projet = await creerProjet({ espaceId: espaceId.value, nom: nom.value.trim(), depot: depotDeSaisie(saisie.value) })
    issue.value = `Projet « ${projet.nom} » créé.`
    nom.value = ''
    saisie.value = saisieDepotVide()
    emit('cree', projet)
  } catch (echec) {
    erreur.value = messageEchec(echec)
  } finally {
    enCours.value = false
  }
}

onMounted(chargerEspaces)
onBeforeUnmount(() => annulation.abort())
</script>

<template>
  <section aria-labelledby="titre-creation-projet" class="creation-projet">
    <h2 id="titre-creation-projet">Créer un projet</h2>
    <div role="alert">
      <p v-if="erreurEspaces" class="status status-down">
        {{ erreurEspaces }}
        <button type="button" class="secondary" @click="chargerEspaces">Recharger les espaces</button>
      </p>
    </div>
    <p v-if="espaces.length > 0" class="muted" data-liens-radar>
      Radar des espaces :
      <template v-for="(espace, rang) in espaces" :key="espace.id">
        <template v-if="rang > 0"> · </template>
        <RouterLink :to="`/espaces/${espace.id}/radar`">{{ espace.nom }}</RouterLink>
      </template>
    </p>
    <p v-if="!erreurEspaces && espaces.length > 0 && administres.length === 0" class="muted">
      Seul un administrateur d’espace peut créer un projet.
    </p>
    <form v-if="administres.length > 0" novalidate data-formulaire="creation-projet" @submit.prevent="creer">
      <div class="champs">
        <label for="creation-espace">Espace</label>
        <select id="creation-espace" v-model="espaceId">
          <option value="">— Choisir un espace —</option>
          <option v-for="espace in administres" :key="espace.id" :value="espace.id">{{ espace.nom }}</option>
        </select>
        <label for="creation-nom">Nom du projet</label>
        <input id="creation-nom" v-model="nom" type="text" maxlength="200" autocomplete="off" required />
      </div>
      <fieldset>
        <legend>Dépôt (facultatif)</legend>
        <SaisieDepot v-model="saisie" prefixe="creation-depot" />
      </fieldset>
      <button type="submit" :disabled="enCours">Créer le projet</button>
    </form>
    <output class="muted" data-issue="creation-projet">{{ issue }}</output>
    <p v-if="erreur" class="status status-down" role="alert" data-erreur="creation-projet">{{ erreur }}</p>
  </section>
</template>

<style scoped>
.creation-projet form { display: grid; gap: 0.75rem; margin-bottom: 0.5rem; }
.champs { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.4rem 0.75rem; align-items: center; }
/* Un fieldset garde par défaut la largeur de son contenu : min-width le laisse suivre la carte. */
fieldset { border: 1px solid var(--border); border-radius: 8px; min-width: 0; }
/* Écran étroit : libellé au-dessus de son champ, sans défilement horizontal à 320 px (RGAA 10.11). */
@media (max-width: 40rem) {
  .champs { grid-template-columns: minmax(0, 1fr); }
}
button[type='submit'] { justify-self: start; }
</style>
