// Signal de sécurité, sa provenance et son historique (COL-02-S1, COL-03-S2) : `GET /api/v1/signals/{id}` par le
// point d'appel commun `appelerApi`. Les impacts du signal sont lus par `impacts.ts`. Aucune règle métier ici
// (arc42 §5.1) : titre, résumé et liens viennent de la source et sont rendus comme texte.
import { appelerApi } from './client'
import { avecIdentifiants } from './identifiants'

/** Paquet affecté déclaré par la source (schéma `AffectedPackage`). */
export interface PaquetAffecte {
  packageKey: string | null
  declaredPurl: boolean
  ecosystem: string | null
  name: string | null
  versions: string[] | null
}

/** Signal et sa provenance (schéma `SignalView`). */
export interface SignalVue {
  id: string
  source: string | null
  externalId: string | null
  title: string | null
  summary: string | null
  severity: string | null
  publishedAt: string | null
  modifiedAt: string | null
  collectedAt: string | null
  currentVersionId: string | null
  aliases: string[] | null
  affected: PaquetAffecte[] | null
}

/** Version historisée d'un avis (schéma `SignalVersionView`). */
export interface VersionSignal {
  id: string
  versionNo: number
  contentHash: string | null
  sourceModifiedAt: string | null
  collectedAt: string | null
}

/** Donnée d'enrichissement rattachée au signal (schéma `EnrichmentView`). */
export interface EnrichissementSignal {
  source: string | null
  reference: string | null
  data: string | null
  fetchedAt: string | null
}

/** Signal, historique et enrichissements (schéma `SignalDetail`). */
export interface SignalDetail {
  signal: SignalVue
  versions: VersionSignal[] | null
  enrichments: EnrichissementSignal[] | null
  exploited: boolean
}

/** Signal, provenance, historique des versions et enrichissements. */
export function fetchSignal(signalId: string, signal?: AbortSignal): Promise<SignalDetail> {
  return avecIdentifiants('Identifiant de signal', [signalId], ([identifiant]) =>
    appelerApi<SignalDetail>(`/api/v1/signals/${identifiant}`, { signal }),
  )
}
