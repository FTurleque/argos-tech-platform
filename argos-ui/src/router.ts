import { createRouter, createWebHashHistory } from 'vue-router'
import AffichageView from './views/AffichageView.vue'
import AlertesView from './views/AlertesView.vue'
import CompteNonCreeView from './views/CompteNonCreeView.vue'
import ConnexionView from './views/ConnexionView.vue'
import InventaireView from './views/InventaireView.vue'
import RechercheView from './views/RechercheView.vue'
import ReglagesPosteView from './views/ReglagesPosteView.vue'
import ProfilsVeilleView from './views/ProfilsVeilleView.vue'
import StatusView from './views/StatusView.vue'
import VeilleView from './views/VeilleView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    /** Vue accessible sans session (ACC-07) ; toute autre vue exige une session. */
    public?: boolean
  }
}

// Historique par « hash » : fonctionne aussi bien servi par un serveur web que chargé par le desktop.
// La garde des vues est posée par installerSession (session/session.ts).
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'status', component: StatusView, meta: { public: true } },
    { path: '/connexion', name: 'connexion', component: ConnexionView, meta: { public: true } },
    { path: '/compte-non-cree', name: 'compte-non-cree', component: CompteNonCreeView, meta: { public: true } },
    { path: '/affichage', name: 'affichage', component: AffichageView },
    { path: '/alertes', name: 'alertes', component: AlertesView },
    { path: '/alertes/:id', name: 'alerte', component: AlertesView },
    { path: '/inventaire', name: 'inventaire', component: InventaireView },
    { path: '/inventaire/:id', name: 'inventaire-projet', component: InventaireView },
    { path: '/poste', name: 'poste', component: ReglagesPosteView },
    { path: '/recherche', name: 'recherche', component: RechercheView },
    { path: '/recherche/vues/:id', name: 'vue-recherche', component: RechercheView },
    { path: '/veille', name: 'veille', component: VeilleView },
    { path: '/veille/profils', name: 'profils-veille', component: ProfilsVeilleView },
  ],
})
