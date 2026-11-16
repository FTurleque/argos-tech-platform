<script setup lang="ts">
// Réglages du poste, propres au client desktop : notifications du poste (NOT-04) et démarrage avec
// la session (Q-2, désactivé par défaut). Ils restent sur le poste et ne modifient aucun autre
// canal d'alerte : les e-mails continuent selon les abonnements.
import { onMounted, onUnmounted, ref } from 'vue'
import { changerReglagesPoste, isDesktop, lireReglagesPoste, surChangementReglages, type ReglagesPoste } from '../platform'

const reglages = ref<ReglagesPoste | null>(null)
const erreur = ref('')
let desabonner: () => void = () => {}

async function changer(cle: 'notificationsPoste' | 'demarrageSession', valeur: boolean): Promise<void> {
  erreur.value = ''
  const appliques = await changerReglagesPoste({ [cle]: valeur })
  if (appliques) reglages.value = appliques
  else erreur.value = 'Réglage non enregistré.'
}

onMounted(async () => {
  desabonner = surChangementReglages((r) => { reglages.value = r })
  reglages.value = await lireReglagesPoste()
})
onUnmounted(() => desabonner())
</script>

<template>
  <section class="card">
    <h1>Réglages du poste</h1>
    <p v-if="!isDesktop" class="muted">Ces réglages n’existent que dans l’application desktop.</p>
    <form v-else-if="reglages" @submit.prevent>
      <p>
        <label>
          <input
            type="checkbox"
            :checked="reglages.notificationsPoste"
            @change="changer('notificationsPoste', ($event.target as HTMLInputElement).checked)"
          />
          Notifications du poste
        </label>
        <br />
        <span class="muted">
          Coupées, les alertes restent comptées dans la zone de notification et visibles dans l’application ;
          vos autres canaux (e-mail) ne changent pas.
        </span>
      </p>
      <p>
        <label>
          <input
            type="checkbox"
            :checked="reglages.demarrageSession"
            :disabled="!reglages.demarrageSessionDisponible"
            @change="changer('demarrageSession', ($event.target as HTMLInputElement).checked)"
          />
          Démarrer avec la session
        </label>
        <br />
        <span class="muted">
          {{ reglages.demarrageSessionDisponible
            ? 'L’application démarre masquée dans la zone de notification à l’ouverture de la session.'
            : 'Non disponible sur ce système.' }}
        </span>
      </p>
      <p v-if="erreur" class="status status-down">{{ erreur }}</p>
    </form>
  </section>
</template>
