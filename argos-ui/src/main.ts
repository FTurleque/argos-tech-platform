import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { demarrerSession, installerSession } from './session/session'
import './assets/main.css'

async function demarrer(): Promise<void> {
  // Un retour du fournisseur d'identité est traité avant la première navigation (createApp().use
  // lance la navigation initiale) : l'adresse est nettoyée et positionnée sur la vue demandée avant
  // que le routeur ne la lise (ACC-07-S2).
  // Le client REST et la garde sont branchés d'abord : la vérification du compte qui suit le retour
  // part avec le jeton porteur (ACC-09) et détecte un compte non créé avant toute vue protégée.
  installerSession(router)
  await demarrerSession()
  createApp(App).use(router).mount('#app')
}

void demarrer()
