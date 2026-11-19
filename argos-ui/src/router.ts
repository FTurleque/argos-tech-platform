import type { Component } from 'vue'
import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
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

/** Une vue et ses adresses, indexées par nom de route : une vue de liste a aussi l'adresse d'un élément. */
type VueAdressee = readonly [vue: Component, adresses: Readonly<Record<string, string>>]

/** Vues accessibles sans session (ACC-07) : état du serveur, connexion et refus d'un compte non créé. */
const VUES_PUBLIQUES: readonly VueAdressee[] = [
  [StatusView, { status: '/' }],
  [ConnexionView, { connexion: '/connexion' }],
  [CompteNonCreeView, { 'compte-non-cree': '/compte-non-cree' }],
]

/** Vues qui exigent une session. */
const VUES_PROTEGEES: readonly VueAdressee[] = [
  [AffichageView, { affichage: '/affichage' }],
  [AlertesView, { alertes: '/alertes', alerte: '/alertes/:id' }],
  [InventaireView, { inventaire: '/inventaire', 'inventaire-projet': '/inventaire/:id' }],
  [ReglagesPosteView, { poste: '/poste' }],
  [RechercheView, { recherche: '/recherche', 'vue-recherche': '/recherche/vues/:id' }],
  [VeilleView, { veille: '/veille' }],
  [ProfilsVeilleView, { 'profils-veille': '/veille/profils' }],
]

/** Une route par adresse de chaque vue ; les vues publiques portent `meta.public`. */
function routes(vues: readonly VueAdressee[], publique: boolean): RouteRecordRaw[] {
  return vues.flatMap(([component, adresses]) =>
    Object.entries(adresses).map(([name, path]) => (publique ? { path, name, component, meta: { public: true } } : { path, name, component })),
  )
}

// Historique par « hash » : fonctionne aussi bien servi par un serveur web que chargé par le desktop.
// La garde des vues est posée par installerSession (session/session.ts).
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [...routes(VUES_PUBLIQUES, true), ...routes(VUES_PROTEGEES, false)],
})
