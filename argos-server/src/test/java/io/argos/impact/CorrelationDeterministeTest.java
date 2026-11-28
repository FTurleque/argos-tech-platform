package io.argos.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.impact.ImpactsEnLot.Perime;
import io.argos.signal.AffectedPackage;
import io.argos.signal.AffectedPackage.VersionRange;
import io.argos.signal.SignalQueries.SignalView;

/** Motif de la résolution par le système d'un impact qui n'est plus vérifié (COR-11). */
class CorrelationDeterministeTest {

    private static final String CLE = "pkg:npm/exemple";
    private static final UUID PROJET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SIGNAL = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COMPOSANT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String PAQUET = ImpactsEnLot.clePaquet(PROJET, CLE);

    private static SignalView signal(List<AffectedPackage> affectes) {
        Instant date = Instant.parse("2026-11-10T00:00:00Z");
        return new SignalView(SIGNAL, "OSV", "GHSA-COR11", "Avis", "Résumé", "HIGH", date, date, date, UUID.randomUUID(),
                List.of(), affectes);
    }

    private static SignalView avisSurLePaquet() {
        return signal(List.of(new AffectedPackage(CLE, true, "npm", "exemple",
                List.of(new VersionRange("SEMVER", "0", "2.0.0", null)), List.of())));
    }

    /** Impact périmé ; {@code composant} nul quand l'import a retiré ou remplacé le composant. */
    private static Perime perime(EtatImpact etat, UUID composant) {
        return new Perime(UUID.randomUUID(), SIGNAL, PROJET, etat, composant, CLE);
    }

    /** COR-11-S1 : composant retiré de l'inventaire (même confirmé), le paquet n'y est plus. */
    @Test
    void cor11S1ComposantRetireDeLInventaire() {
        assertEquals(MotifSysteme.COMPOSANT_RETIRE,
                CorrelationDeterministe.motif(perime(EtatImpact.CONFIRME, null), avisSurLePaquet(), Set.of(), Set.of()));
        assertEquals(MotifSysteme.COMPOSANT_RETIRE,
                CorrelationDeterministe.motif(perime(EtatImpact.OUVERT, null), avisSurLePaquet(), Set.of(), Set.of()));
    }

    /** COR-11-S2 : le paquet reste présent, mis à jour vers une version hors plage. */
    @Test
    void cor11S2ComposantMisAJourHorsPlage() {
        assertEquals(MotifSysteme.VERSION_HORS_PLAGE,
                CorrelationDeterministe.motif(perime(EtatImpact.OUVERT, null), avisSurLePaquet(), Set.of(), Set.of(PAQUET)));
    }

    /** Une autre version du paquet reste affectée : l'ancien composant est retiré, pas hors plage. */
    @Test
    void paquetEncoreAffecteParUneAutreVersion() {
        assertEquals(MotifSysteme.COMPOSANT_RETIRE, CorrelationDeterministe.motif(perime(EtatImpact.OUVERT, null),
                avisSurLePaquet(), Set.of(PAQUET), Set.of(PAQUET)));
    }

    /** COR-11-S3 (avis modifié) : le composant est toujours là, mais l'avis ne l'affecte plus. */
    @Test
    void cor11S3AvisModifie() {
        assertEquals(MotifSysteme.AVIS_MODIFIE,
                CorrelationDeterministe.motif(perime(EtatImpact.OUVERT, COMPOSANT), avisSurLePaquet(), Set.of(), Set.of(PAQUET)));
    }

    /** COR-11-S3 (avis retiré) : l'avis ne déclare plus aucun paquet affecté, quel que soit l'inventaire. */
    @Test
    void cor11S3AvisRetire() {
        assertEquals(MotifSysteme.AVIS_RETIRE,
                CorrelationDeterministe.motif(perime(EtatImpact.OUVERT, COMPOSANT), signal(List.of()), Set.of(), Set.of(PAQUET)));
        assertEquals(MotifSysteme.AVIS_RETIRE,
                CorrelationDeterministe.motif(perime(EtatImpact.CONFIRME, null), signal(List.of()), Set.of(), Set.of()));
    }
}
