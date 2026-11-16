import { createRouter, createWebHashHistory } from 'vue-router'
import AffichageView from './views/AffichageView.vue'
import AlertesView from './views/AlertesView.vue'
import ReglagesPosteView from './views/ReglagesPosteView.vue'
import StatusView from './views/StatusView.vue'

// Historique par « hash » : fonctionne aussi bien servi par un serveur web que chargé par le desktop.
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'status', component: StatusView },
    { path: '/affichage', name: 'affichage', component: AffichageView },
    { path: '/alertes', name: 'alertes', component: AlertesView },
    { path: '/alertes/:id', name: 'alerte', component: AlertesView },
    { path: '/poste', name: 'poste', component: ReglagesPosteView },
  ],
})
