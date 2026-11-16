<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { isDesktop, surOuvertureAlerte } from './platform'
import { demarrerSuiviPoste, etatPoste, oublierCoupure } from './contenu/suivi-poste'

const router = useRouter()
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
let desabonner: () => void = () => {}

onMounted(() => {
  // Clic sur une notification d'alerte : la fenêtre affiche cette alerte (NOT-02).
  desabonner = surOuvertureAlerte((id) => {
    if (UUID.test(id)) void router.push(`/alertes/${id}`)
  })
  demarrerSuiviPoste()
})
onUnmounted(() => desabonner())
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-name">ARGOS</span>
        <span class="brand-motto">Observer. Corréler. Comprendre. Anticiper.</span>
      </div>
      <nav class="nav" aria-label="Navigation principale">
        <RouterLink to="/">État</RouterLink>
        <RouterLink to="/affichage">Mise en page</RouterLink>
        <RouterLink v-if="isDesktop" to="/alertes">
          Alertes<template v-if="etatPoste.ouvertes > 0"> ({{ etatPoste.ouvertes }})</template>
        </RouterLink>
        <RouterLink v-if="isDesktop" to="/poste">Poste</RouterLink>
      </nav>
      <span class="badge">{{ isDesktop ? 'Application desktop' : 'Portail web' }}</span>
    </header>
    <main class="content">
      <p v-if="isDesktop && etatPoste.connexion === 'HORS_LIGNE'" class="status status-down" role="status">
        Hors ligne : serveur ARGOS injoignable. Les alertes émises pendant la coupure seront présentées au retour de la connexion.
      </p>
      <p v-else-if="isDesktop && etatPoste.connexion === 'NON_AUTORISE'" class="status status-down" role="status">
        Connexion requise pour recevoir les alertes du poste.
      </p>
      <p v-else-if="isDesktop && etatPoste.recuesPendantCoupure > 0" class="status status-up" role="status">
        Connexion rétablie : {{ etatPoste.recuesPendantCoupure }}
        {{ etatPoste.recuesPendantCoupure === 1 ? 'alerte émise' : 'alertes émises' }} pendant la coupure.
        <RouterLink to="/alertes" @click="oublierCoupure()">Voir les alertes</RouterLink>
      </p>
      <RouterView />
    </main>
  </div>
</template>
