package io.argos.api.recherche;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mots et filtres d'une recherche de veille (RCH-01, RCH-02), tels que saisis et tels qu'enregistrés
 * dans une vue (RCH-04). Chaque filtre est facultatif ; les filtres se combinent par ET, les valeurs
 * d'un même filtre par OU.
 *
 * @param texte        mots recherchés, 500 caractères au plus (syntaxe web : mots, "expression", or, -exclusion)
 * @param projets      projets de l'utilisateur concernés par l'élément
 * @param technologies technologies (Oracle, java, npm…)
 * @param types        AVIS_SECURITE, ARTICLE, CHANGEMENT_PAGE
 * @param gravite      gravité minimale : LOW, MODERATE, MEDIUM, HIGH, CRITICAL
 * @param horizonJours éléments des N derniers jours (1 à 3 650)
 * @param profils      profils de veille publiés (identifiants du catalogue)
 */
@Schema(name = "CriteresRecherche", description = "Mots et filtres d'une recherche de veille")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CriteresRecherche(String texte, List<UUID> projets, List<String> technologies, List<String> types,
        String gravite, Integer horizonJours, List<String> profils) {
}
