import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { demarrerSession, installerSession } from './session/session'
import './assets/main.css'

async function demarrer(): Promise<void> {
  // Un retour du fournisseur d'identité est traité avant la première navigation : l'adresse est
  // nettoyée (ni code ni state) puis la vue demandée est affichée (ACC-07-S2).
  // Le client REST et la garde sont branchés d'abord : la vérification du compte qui suit le retour
  // part avec le jeton porteur (ACC-09) et détecte un compte non créé avant toute vue protégée.
  installerSession(router)
  const vue = await demarrerSession()
  // L'historique par « hash » lit l'adresse à sa création (import de router.ts), avant le nettoyage
  // du retour : la première vue est fixée explicitement, la vue demandée si la connexion aboutit,
  // sinon celle de l'adresse nettoyée (la garde y substitue l'écran de connexion et son message).
  await router.replace(vue ?? (window.location.hash.slice(1) || '/'))
  createApp(App).use(router).mount('#app')
}

void demarrer()
