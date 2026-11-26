// Liens des alertes, des résumés et des rappels vers l'IHM (ALR-09-S3, décision D5) : chaque gabarit déclaré par le
// serveur dans `alerting/LiensIhm.java` (constante `String` dont la valeur commence par `/#/` et contient `{id}`)
// correspond à une route de `src/router.ts` (`:param` ↔ `{id}`). La suite échoue si un gabarit n'a pas de route, ou si
// le fichier n'en déclare aucun.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { ROUTES } from './support/routes.mjs'

const LIENS_IHM = new URL('../../argos-server/src/main/java/io/argos/alerting/LiensIhm.java', import.meta.url)
const PREFIXE_HASH = '/#'
const GABARIT = /\bString\s+(\w+)\s*=\s*"(\/#\/[^"]*\{id\}[^"]*)"/g
const PARAMETRE_ROUTE = /^:\w+$/
const PARAMETRE_GABARIT = '{id}'

/** Gabarits déclarés dans un source Java : nom de la constante et valeur. */
export function gabarits(source) {
  return [...source.matchAll(GABARIT)].map(([, nom, valeur]) => ({ nom, valeur }))
}

/** Vrai si le chemin d'un gabarit (sans `/#`) correspond au chemin d'une route, segment par segment. */
export function correspond(cheminGabarit, cheminRoute) {
  const segmentsGabarit = cheminGabarit.split('/')
  const segmentsRoute = cheminRoute.split('/')
  if (segmentsGabarit.length !== segmentsRoute.length) return false
  return segmentsGabarit.every((segment, rang) =>
    segment === PARAMETRE_GABARIT ? PARAMETRE_ROUTE.test(segmentsRoute[rang]) : segment === segmentsRoute[rang],
  )
}

/** Gabarits sans route correspondante. */
export function gabaritsSansRoute(declares, routes) {
  return declares.filter(({ valeur }) => !routes.some((route) => correspond(valeur.slice(PREFIXE_HASH.length), route.path)))
}

test('ALR-09-S3 : chaque gabarit de lien du serveur correspond à une route de l’IHM', () => {
  const declares = gabarits(readFileSync(LIENS_IHM, 'utf8'))
  assert.ok(declares.length > 0, 'LiensIhm.java ne déclare aucun gabarit « /#/…{id}… »')
  assert.deepEqual(gabaritsSansRoute(declares, ROUTES), [])
})

test('la vérification échoue pour un gabarit sans route et ne retient que les gabarits « /#/…{id}… »', () => {
  const source = [
    'static final String SIGNAL = "/#/signaux/{id}";',
    'static final String INCONNU = "/#/nulle-part/{id}";',
    'private static final String PARAMETRE = "{id}";',
    'static final String SANS_HASH = "/signaux/{id}";',
  ].join('\n')
  const declares = gabarits(source)
  assert.deepEqual(declares.map(({ nom }) => nom), ['SIGNAL', 'INCONNU'])
  assert.deepEqual(gabaritsSansRoute(declares, ROUTES).map(({ nom }) => nom), ['INCONNU'])
  assert.equal(correspond('/espaces/{id}/radar', '/espaces/:id/radar'), true)
  assert.equal(correspond('/espaces/{id}', '/espaces/:id/radar'), false)
})
