package io.argos.regintel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Texte officiel enregistré (table {@code texte_officiel}) : données publiques collectées dans les jeux
 * de données JORF de la DILA (Licence Ouverte). Les chaînes sont des données externes non fiables.
 *
 * @param dateEffet        date d'entrée en vigueur extraite du texte, absente : « date à confirmer »
 * @param motifDateEffet   passage ou règle qui a permis l'extraction, absent si aucune date
 */
public record TexteOfficiel(UUID id, String identifiant, String nature, String numero, String nor, String ministere,
        String titre, LocalDate datePublication, LocalDate dateSignature, LocalDate dateEffet, String motifDateEffet,
        List<Code> codes, List<String> articlesModifies, String extrait) {

    public TexteOfficiel {
        codes = List.copyOf(codes);
        articlesModifies = List.copyOf(articlesModifies);
    }

    /** Code consolidé modifié par le texte. */
    public record Code(String identifiant, String libelle) {
    }
}
