// Rendu côté serveur des mises en page pour les tests `node --test` (sans navigateur ni réseau).
//
// Vite compile les composants Vue en mode SSR (serveur en mode intergiciel : aucun port ouvert, ni
// HMR ni surveillance des fichiers) ; `vue/server-renderer` produit le HTML de chaque mise en page
// pour un même jeu de données.
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')

/**
 * Ouvre un compilateur Vite et renvoie de quoi rendre les mises en page ; `fermer()` le libère.
 */
export async function ouvrirRendu() {
  const vite = await createServer({
    root: RACINE,
    configFile: join(RACINE, 'vite.config.ts'),
    logLevel: 'error',
    appType: 'custom',
    server: { middlewareMode: true, hmr: false, ws: false, watch: null },
  })
  const { MISES_EN_PAGE } = await vite.ssrLoadModule('/src/mises-en-page/registre.ts')
  const { VEILLE_APERCU } = await vite.ssrLoadModule('/src/contenu/apercu.ts')
  const { default: MiseEnPage } = await vite.ssrLoadModule('/src/mises-en-page/MiseEnPage.vue')

  return {
    misesEnPage: MISES_EN_PAGE,
    veilleApercu: VEILLE_APERCU,
    /** HTML d'une mise en page du registre. */
    rendre(identifiant, veille = VEILLE_APERCU, niveauTitre = 2) {
      const composant = MISES_EN_PAGE[identifiant]
      return renderToString(createSSRApp({ render: () => h(composant, { veille, niveauTitre }) }))
    },
    /** HTML du sélecteur central pour un identifiant quelconque. */
    rendreSelecteur(identifiant, veille = VEILLE_APERCU) {
      return renderToString(createSSRApp({ render: () => h(MiseEnPage, { identifiant, veille, niveauTitre: 2 }) }))
    },
    fermer: () => vite.close(),
  }
}
