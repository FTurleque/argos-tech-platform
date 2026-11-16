package io.argos.signal.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Source de veille web : flux RSS/Atom ou page surveillée, d'un espace ou personnelle (WEB-01,
 * WEB-02). L'état {@code EN_ERREUR} porte le motif du dernier échec (WEB-03-S2).
 */
public record SourceWeb(UUID id, String portee, UUID espaceId, UUID proprietaireId, String type, String adresse,
        String libelle, String etat, int echecsConsecutifs, String derniereErreur, Instant derniereCollecteLe,
        Instant dernierSuccesLe, Instant prochaineCollecteLe, Instant creeLe) {

    public static final String ESPACE = "ESPACE";
    public static final String PERSONNELLE = "PERSONNELLE";
    public static final String FLUX = "FLUX";
    public static final String PAGE = "PAGE";

    public boolean personnelle() {
        return PERSONNELLE.equals(portee);
    }
}
