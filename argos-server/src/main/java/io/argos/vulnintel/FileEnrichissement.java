package io.argos.vulnintel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;

/**
 * File persistante des signaux à enrichir (table {@code enrichissement_attente}, COL-04, ADR-0039) : chaque
 * opération est une transaction courte, jamais ouverte pendant un appel à une source secondaire.
 */
@ApplicationScoped
public class FileEnrichissement {

    /** Longueur maximale du motif d'échec enregistré. */
    private static final int MOTIF_MAX = 1000;

    /** Signal en attente d'enrichissement, avec la date de sa dernière demande. */
    public record Attente(UUID signalId, int tentatives, Instant demandeLe) {
    }

    @Inject
    Jdbc jdbc;

    /**
     * Place le signal en file, échu à {@code maintenant} ; un signal déjà en file devient échu sans perdre ses tentatives
     * et porte la date de cette nouvelle demande.
     */
    @Transactional
    public void demander(UUID signalId, Instant maintenant) {
        jdbc.modifier("""
                INSERT INTO enrichissement_attente (signal_id, demande_le, tentatives, prochain_essai_le)
                VALUES (?, ?, 0, ?)
                ON CONFLICT (signal_id) DO UPDATE SET demande_le = excluded.demande_le,
                    prochain_essai_le = least(enrichissement_attente.prochain_essai_le, excluded.prochain_essai_le)
                """, Jdbc.parametres(signalId, Conversions.utc(maintenant), Conversions.utc(maintenant)));
    }

    /** Signaux échus à {@code maintenant}, les plus anciennement échus d'abord, au plus {@code lot}. */
    @Transactional
    public List<Attente> echus(Instant maintenant, int lot) {
        return jdbc.lister("""
                SELECT signal_id, tentatives, demande_le FROM enrichissement_attente WHERE prochain_essai_le <= ?
                ORDER BY prochain_essai_le, signal_id LIMIT ?
                """, Jdbc.parametres(Conversions.utc(maintenant), lot),
                resultat -> new Attente(resultat.getObject(1, UUID.class), resultat.getInt(2), Conversions.instant(resultat, 3)));
    }

    /**
     * Retire le signal de la file (toutes les sources ont répondu, ou ses tentatives sont épuisées), sauf s'il a été
     * demandé de nouveau depuis sa lecture : il reste alors échu pour le passage suivant.
     */
    @Transactional
    public void terminer(FileEnrichissement.Attente attente) {
        jdbc.modifier("DELETE FROM enrichissement_attente WHERE signal_id = ? AND demande_le = ?",
                Jdbc.parametres(attente.signalId(), Conversions.utc(attente.demandeLe())));
    }

    /** Reporte le signal à {@code prochainEssai}, avec son nombre de tentatives et le motif. */
    @Transactional
    public void reporter(UUID signalId, int tentatives, Instant prochainEssai, String motif) {
        jdbc.modifier("""
                UPDATE enrichissement_attente SET tentatives = ?, prochain_essai_le = ?, derniere_erreur = ?
                WHERE signal_id = ?
                """, Jdbc.parametres(tentatives, Conversions.utc(prochainEssai), Conversions.couper(motif, MOTIF_MAX), signalId));
    }

    /**
     * Replace en file, au plus {@code limite}, les signaux à identifiant CVE qui n'ont aucun enrichissement et ne sont
     * pas déjà en file ; renvoie leur nombre.
     */
    @Transactional
    public int rattraper(Instant maintenant, int limite) {
        return jdbc.modifier("""
                INSERT INTO enrichissement_attente (signal_id, demande_le, tentatives, prochain_essai_le)
                SELECT DISTINCT a.signal_id, ?::timestamptz, 0, ?::timestamptz FROM signal_alias a
                WHERE a.alias LIKE 'CVE-%'
                  AND NOT EXISTS (SELECT 1 FROM enrichment e WHERE e.signal_id = a.signal_id)
                  AND NOT EXISTS (SELECT 1 FROM enrichissement_attente f WHERE f.signal_id = a.signal_id)
                LIMIT ?
                ON CONFLICT (signal_id) DO NOTHING
                """, Jdbc.parametres(Conversions.utc(maintenant), Conversions.utc(maintenant), limite));
    }
}
