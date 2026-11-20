// Banc d'essai des mises en page (voir banc/index.html) : jeu d'exemple, aucune requête au serveur.
import { createApp, h as creerNoeud } from 'vue'
import { VEILLE_APERCU } from '../src/contenu/apercu'
import MiseEnPage from '../src/mises-en-page/MiseEnPage.vue'

const identifiant = new URLSearchParams(window.location.search).get('mise-en-page') ?? 'classique'
document.body.style.margin = '0'

createApp({
  render: () =>
    creerNoeud('main', { style: 'padding: 16px; max-width: 1180px; margin: 0 auto; font-family: system-ui, sans-serif' }, [
      creerNoeud('h1', { style: 'font-size: 1.1rem; margin: 0 0 12px' }, 'Banc d’essai des mises en page (données d’exemple)'),
      creerNoeud(MiseEnPage, { identifiant, veille: VEILLE_APERCU, niveauTitre: 2 }),
    ]),
}).mount('#app')
