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
 * <p>Point d'extension en lecture seule utilisé par le serveur MCP ({@code get_regulatory_changes}),
 * implémenté par {@link FichesEnBase} : les fiches sont lues sous la RLS avec le compte de l'appelant.
 */
public interface FichesReglementaires {

    /** État de validation d'une fiche. Seule une fiche {@code VALIDEE} peut être diffusée (PRF-06, MCP-07). */
    enum Etat { EN_ATTENTE_VALIDATION, VALIDEE, REJETEE }

    /**
     * Fiche réglementaire.
     *
     * @param id                  identifiant stable
     * @param espaceId            espace dont les domaines métier sont concernés
     * @param domaines            domaines métier rattachés
     * @param etat                état de validation par l'expert métier
     * @param titreOfficiel       titre du texte officiel, tel que publié par la source (contenu externe)
     * @param referenceOfficielle identifiant officiel du texte ({@code JORFTEXT…})
     * @param nor                 numéro NOR, absent si inconnu
     * @param resume              résumé de la fiche, relu par l'expert métier, absent s'il reste à rédiger
     * @param publication         date de publication du texte
     * @param effet               date d'entrée en vigueur, absente si elle reste à confirmer
     * @param source              source du texte (identifiant court, {@code JORF})
     * @param lienOfficiel        adresse du texte publié sur Légifrance
     */
    record Fiche(UUID id, UUID espaceId, List<String> domaines, Etat etat, String titreOfficiel, String referenceOfficielle,
            String nor, String resume, LocalDate publication, LocalDate effet, String source, String lienOfficiel) {

        public Fiche {
            domaines = List.copyOf(domaines);
        }
    }

    /**
     * Fiches validées visibles du compte dans les espaces donnés, et seulement ceux-là, éventuellement
     * limitées à un domaine, des plus récentes aux plus anciennes publications. Aucune fiche en attente ou
     * rejetée n'est renvoyée (PRF-06, MCP-07).
     */
    List<Fiche> fiches(UUID compte, Set<UUID> espaces, Optional<String> domaine);
}
