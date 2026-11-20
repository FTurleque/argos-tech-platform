// Routes déclarées dans src/router.ts, lues dans le source (le routeur importe des composants .vue, que
// `node --test` ne charge pas) : tables `VUES_PUBLIQUES` et `VUES_PROTEGEES`, une entrée `[Vue, { nom: 'chemin' }]`
// par vue.
import { readFileSync } from 'node:fs'

const ROUTEUR = readFileSync(new URL('../../src/router.ts', import.meta.url), 'utf8')

function table(nom) {
  const debut = ROUTEUR.indexOf(`const ${nom}`)
  if (debut < 0) throw new Error(`table ${nom} absente de router.ts`)
  const fin = ROUTEUR.indexOf('\n]', debut)
  return ROUTEUR.slice(debut, fin)
}

function routesDe(nom, publique) {
  const routes = []
  for (const [, composant, adresses] of table(nom).matchAll(/\[(\w+), \{ ([^}]*) \}\]/g)) {
    for (const [, name, path] of adresses.matchAll(/'?([\w-]+)'?: '([^']+)'/g)) {
      routes.push({ path, name, composant, publique })
    }
  }
  return routes
}

/** Routes de l'IHM : chemin, nom, composant (nom de la vue) et accès sans session. */
export const ROUTES = [...routesDe('VUES_PUBLIQUES', true), ...routesDe('VUES_PROTEGEES', false)]

/** Route d'un nom donné, ou `undefined`. */
export function route(name) {
  return ROUTES.find((route) => route.name === name)
}
