package io.argos.signal.opportunite;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Opportunité collectée par une source vérifiée d'un profil : appel à projets, résidence, aide,
 * information sur les droits ou le statut, événement. Les textes sont du texte brut issu d'un contenu
 * externe non fiable ; le lien est une adresse http(s) ou absent.
 *
 * @param criteres    critères tels qu'énoncés par l'organisme (texte brut)
 * @param lien        lien vers le texte officiel
 * @param disciplines identifiants des disciplines du profil
 * @param territoires identifiants des territoires du profil ({@code france} : toute la France)
 * @param dateLimite  date limite de candidature ; absente : « date à confirmer »
 */
public record Opportunite(UUID id, UUID sourceId, String sourceLibelle, String rubrique, String titre,
        String organisme, String criteres, String lien, List<String> disciplines, List<String> territoires,
        LocalDate dateLimite, Instant recuLe) {

    public Opportunite {
        disciplines = List.copyOf(disciplines);
        territoires = List.copyOf(territoires);
    }
}
