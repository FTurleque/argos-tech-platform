package io.argos.regintel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture des fiches réglementaires des espaces (module {@code reg-intel}, arc42 §5 ; capacité
 * {@code veille-reglementaire}, ADR-0018).
 *
 * <p>Point d'extension en lecture seule utilisé par le serveur MCP ({@code get_regulatory_changes}).
 * La collecte, la préparation et la validation des fiches relèvent du changement
 * {@code suivre-veille-reglementaire} ; tant qu'il n'est pas livré, l'implémentation par défaut
 * {@link FichesReglementairesNonRenseignees} ne connaît aucune fiche.
 */
public interface FichesReglementaires {

    /** État de validation d'une fiche. Seule une fiche {@code VALIDEE} peut être diffusée (PRF-06, MCP-07). */
    enum Etat { EN_ATTENTE_VALIDATION, VALIDEE, REJETEE }

    /**
     * Fiche réglementaire.
     *
     * @param id                  identifiant stable
     * @param espaceId            espace dont le domaine métier est concerné
     * @param domaine             domaine métier
     * @param etat                état de validation par l'expert métier
     * @param titreOfficiel       titre du texte officiel, tel que publié par la source (contenu externe)
     * @param referenceOfficielle identifiant officiel du texte (par exemple NOR)
     * @param resume              résumé de la fiche, relu par l'expert métier
     * @param publication         date de publication du texte
     * @param effet               date d'entrée en vigueur, absente si inconnue
     * @param source              source du texte (identifiant court, par exemple {@code legifrance})
     */
    record Fiche(UUID id, UUID espaceId, String domaine, Etat etat, String titreOfficiel, String referenceOfficielle,
            String resume, LocalDate publication, LocalDate effet, String source) {
    }

    /** Fiches des espaces donnés, et seulement de ceux-là, éventuellement limitées à un domaine. */
    List<Fiche> fiches(Set<UUID> espaces, Optional<String> domaine);
}
