<script setup lang="ts">
// Écran de connexion (changement connecter-ihm-oidc : ACC-07, ACC-08-S3, ACC-10).
// Aucun mot de passe n'est saisi dans Argos : la connexion se fait chez le fournisseur d'identité.
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isDesktop } from '../platform'
import { cheminInterne } from '../session/oidc'
import { etatSession, seConnecter } from '../session/session'

const route = useRoute()
const router = useRouter()
const retour = computed(() => cheminInterne(route.query.retour))
const enCours = computed(() => etatSession.etat === 'CONNEXION_EN_COURS')

async function connecter(): Promise<void> {
  const vue = await seConnecter(retour.value)
  if (vue !== null) await router.replace(vue)
}
</script>

<template>
  <section class="card">
    <h1>Connexion</h1>

    <p v-if="etatSession.etat === 'EXPIREE'" class="status status-down" role="status">
      <strong>Session expirée</strong>
      <span>{{ etatSession.message }}</span>
    </p>
    <p v-else-if="etatSession.etat === 'REFUSEE' || etatSession.etat === 'ERREUR'" class="status status-down" role="alert">
      <strong>Connexion impossible</strong>
      <span>{{ etatSession.message }}</span>
    </p>
    <p v-else-if="etatSession.etat === 'CONNECTE'" class="status status-up" role="status">
      Vous êtes connecté<template v-if="etatSession.compte?.nomAffiche"> en tant que {{ etatSession.compte.nomAffiche }}</template>.
    </p>

    <p class="muted">
      Cette vue exige une session. La connexion se fait auprès du fournisseur d'identité d'ARGOS :
      aucun mot de passe n'est saisi dans l'application.
    </p>
    <p v-if="enCours && isDesktop" class="muted" role="status">
      Poursuivez la connexion dans votre navigateur, puis revenez à ARGOS.
    </p>
    <p v-else-if="enCours" class="muted" role="status">Redirection vers le fournisseur d'identité…</p>

    <div class="actions">
      <button v-if="etatSession.etat !== 'CONNECTE'" type="button" @click="connecter">
        {{ enCours ? 'Relancer la connexion' : 'Se connecter' }}
      </button>
      <RouterLink v-else :to="retour">Continuer</RouterLink>
    </div>
  </section>
</template>
