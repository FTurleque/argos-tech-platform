import { createRouter, createWebHashHistory } from 'vue-router'
import AffichageView from './views/AffichageView.vue'
import StatusView from './views/StatusView.vue'

// Historique par « hash » : fonctionne aussi bien servi par un serveur web que chargé par le desktop.
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'status', component: StatusView },
    { path: '/affichage', name: 'affichage', component: AffichageView },
  ],
})
