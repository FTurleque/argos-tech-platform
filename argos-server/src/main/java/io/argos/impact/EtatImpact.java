package io.argos.impact;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * État de traitement d'un impact (COR-09, décision J5 du porteur du projet, 2026-11-19), distinct de son niveau de
 * confiance ({@code EXACT}, {@code PROBABLE}, {@code A_VERIFIER}). Transitions permises (COR-10, DC-5) :
 * {@code OUVERT} → {@code CONFIRME}, {@code ECARTE}, {@code RESOLU} ; {@code CONFIRME} → {@code RESOLU},
 * {@code ECARTE} ; {@code ECARTE} → {@code OUVERT} ; {@code RESOLU} → {@code OUVERT}. Aucun état n'est terminal.
 */
public enum EtatImpact {
    OUVERT, CONFIRME, ECARTE, RESOLU;

    /** États éligibles aux alertes et restitués par défaut dans les listes (ALR-07, COR-12-S2). */
    public static final Set<EtatImpact> ACTIFS = Set.copyOf(EnumSet.of(OUVERT, CONFIRME));

    /** Tous les états. */
    public static final Set<EtatImpact> TOUS = Set.copyOf(EnumSet.allOf(EtatImpact.class));

    private static final Map<EtatImpact, Set<EtatImpact>> SUIVANTS = Map.of(
            OUVERT, Set.copyOf(EnumSet.of(CONFIRME, ECARTE, RESOLU)),
            CONFIRME, Set.copyOf(EnumSet.of(RESOLU, ECARTE)),
            ECARTE, Set.of(OUVERT),
            RESOLU, Set.of(OUVERT));

    /** États atteignables depuis celui-ci par une transition demandée par un utilisateur autorisé. */
    public Set<EtatImpact> suivants() {
        return SUIVANTS.get(this);
    }

    /**
     * États atteignables par un utilisateur selon que le composant de l'impact est encore dans l'inventaire : un impact
     * dont le composant a été retiré ne redevient ni ouvert ni confirmé (invariant INV-C3, décision J5).
     */
    public Set<EtatImpact> suivants(boolean composantPresent) {
        if (composantPresent) {
            return suivants();
        }
        EnumSet<EtatImpact> inactifs = EnumSet.noneOf(EtatImpact.class);
        suivants().stream().filter(etat -> !ACTIFS.contains(etat)).forEach(inactifs::add);
        return Set.copyOf(inactifs);
    }

    /** État désigné par son code ({@code OUVERT}…), sans tenir compte de la casse ni des espaces. */
    public static Optional<EtatImpact> depuis(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException erreur) {
            return Optional.empty();
        }
    }
}
