// Données des parcours, injectées une fois par l'API et par SQL (support/donnees.ts).
import { test as setup } from '@playwright/test'
import { preparerRecherche } from '../support/donnees'

setup('jeu de données de la recherche', async () => {
  await preparerRecherche()
})
