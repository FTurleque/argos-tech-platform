import { createRouter, createWebHashHistory } from 'vue-router'
import StatusView from './views/StatusView.vue'

// Historique par « hash » : fonctionne aussi bien servi par un serveur web que chargé par le desktop.
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'status', component: StatusView },
  ],
})
