import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { demarrerSession, installerSession } from './session/session'
import './assets/main.css'

async function demarrer(): Promise<void> {
  // Un retour du fournisseur d'identité est traité avant la première navigation : l'adresse est
  // nettoyée et positionnée sur la vue demandée avant que le routeur ne la lise (ACC-07-S2).
  await demarrerSession()
  installerSession(router)
  createApp(App).use(router).mount('#app')
}

void demarrer()
