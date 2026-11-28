package io.argos.mcp;

import static io.argos.mcp.OutilsSecurite.AVERTISSEMENT;
import static io.argos.mcp.OutilsSecurite.parametres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.GardeFou;
import io.argos.catalog.profil.TypeGardeFou;
import io.argos.regintel.FicheAnalyse;
import io.argos.regintel.FichesReglementaires;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

/**
 * Outil MCP {@code get_regulatory_changes} avec les garde-fous du profil {@code expert-metier}
 * (MCP-07, PRF-06, ADR-0018) : seules les fiches validées par un expert sont renvoyées, quel que
 * soit l'état fourni par la veille réglementaire, et chaque réponse rappelle qu'elle ne constitue
 * pas un avis juridique.
 */
public class OutilsReglementaires {

    /** Profil dont les garde-fous s'appliquent aux fiches réglementaires. */
    static final String PROFIL = "expert-metier";

    private static final String VALIDATION_PAR_DEFAUT =
            "Une fiche n'est diffusée qu'après validation par l'expert métier référent (ADR-0018).";
    private static final String AVIS_PAR_DEFAUT =
            "Les fiches informent ; elles ne constituent pas un avis juridique (ADR-0018).";

    /** Fiche réglementaire validée, avec la mention de sa validation par l'expert métier (PRF-06-S2). */
    public record FicheReglementaire(UUID ficheId, UUID espaceId, String domaine, List<String> domaines,
            String referenceOfficielle, String nor, String publication, String effet, String source, String lienOfficiel,
            String resumeValide, String mentionValidation, TexteExterne titreOfficiel) {
    }

    /** Réponse de {@code get_regulatory_changes}. */
    public record ChangementsReglementaires(int nombre, List<String> gardeFous, List<FicheReglementaire> fiches) {
    }

    @Inject
    GardeAppels garde;

    @Inject
    BalisageExterne balisage;

    @Inject
    FichesReglementaires fiches;

    @Inject
    CatalogueProfils profils;

    @Tool(name = "get_regulatory_changes", structuredContent = true,
            annotations = @Tool.Annotations(title = "Évolutions réglementaires", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Évolutions réglementaires des domaines métier des espaces de l'utilisateur : uniquement des"
                    + " fiches validées par un expert métier, avec dates de publication et d'entrée en vigueur. Ces fiches"
                    + " informent et ne constituent pas un avis juridique." + AVERTISSEMENT)
    public ChangementsReglementaires getRegulatoryChanges(
            @ToolArg(name = "domain", description = "Domaine métier, facultatif.", required = false) String domaine,
            @ToolArg(name = "limit", description = "Nombre maximal de fiches (1 à 100, 20 par défaut).", required = false) Integer limite) {
        return garde.appeler("get_regulatory_changes", parametres("domain", domaine, "limit", limite), appelant -> {
            Optional<String> domaineRetenu = domaine == null || domaine.isBlank() ? Optional.empty()
                    : Optional.of(Parametres.obligatoire("domain", domaine));
            int nombreMax = Parametres.borne("limit", limite, 20, 1, 100);
            List<FicheReglementaire> validees = fiches.fiches(appelant.accountId(), appelant.espacesMembre(), domaineRetenu).stream()
                    // Garde-fou VALIDATION_HUMAINE : aucune fiche non validée n'est diffusée (MCP-07-S1).
                    .filter(fiche -> fiche.etat() == FichesReglementaires.Etat.VALIDEE)
                    .filter(fiche -> appelant.memberOf(fiche.espaceId()))
                    .limit(nombreMax)
                    .map(fiche -> new FicheReglementaire(fiche.id(), fiche.espaceId(),
                            domaineRetenu.orElse(fiche.domaines().isEmpty() ? null : fiche.domaines().get(0)), fiche.domaines(),
                            fiche.referenceOfficielle(), fiche.nor(), fiche.publication() == null ? null : fiche.publication().toString(),
                            fiche.effet() == null ? "date à confirmer" : fiche.effet().toString(), fiche.source(), fiche.lienOfficiel(),
                            fiche.resume(), FicheAnalyse.MENTION_VALIDEE, balisage.baliser(fiche.source(), fiche.titreOfficiel())))
                    .toList();
            return new ChangementsReglementaires(validees.size(), gardeFous(), validees);
        });
    }

    /** Textes des garde-fous du profil, ou textes par défaut s'il n'est pas publié. */
    List<String> gardeFous() {
        List<GardeFou> declares = profils.trouver(PROFIL).map(profil -> profil.gardeFous()).orElse(List.of());
        String validation = texte(declares, TypeGardeFou.VALIDATION_HUMAINE, VALIDATION_PAR_DEFAUT);
        String avis = texte(declares, TypeGardeFou.AUCUN_AVIS_JURIDIQUE, AVIS_PAR_DEFAUT);
        return List.of(validation, avis);
    }

    private static String texte(List<GardeFou> declares, TypeGardeFou type, String defaut) {
        return declares.stream().filter(gardeFou -> gardeFou.type() == type).map(GardeFou::description).findFirst().orElse(defaut);
    }
}
