package io.argos.api.veille;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.argos.api.preference.ResolutionPreferences;
import io.argos.api.security.Access;
import io.argos.api.security.Caller;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.catalog.profil.Rubrique;
import io.argos.impact.ImpactQueries;
import io.argos.regintel.FicheAnalyse;
import io.argos.regintel.FichesReglementaires;
import io.argos.signal.SignalQueries;
import io.argos.workspace.preference.PreferencesService;
import io.quarkus.security.Authenticated;

/**
 * Veille de l'utilisateur courant restituée comme l'union de ses profils actifs (PRF-02, PRF-04, PRF-06,
 * DC-2 du changement {@code personnaliser-profils-et-affichage}).
 *
 * <p>Ordre d'application : les droits d'abord (lectures sous la RLS avec le compte de l'appelant, limitées
 * à ses espaces : ADR-0016, PRF-05), puis les profils actifs, puis leurs garde-fous, enfin la fusion sans
 * doublon avec les rubriques de chaque profil ({@link UnionProfils}). La mise en page ne reçoit que ces
 * éléments, avec leurs mentions obligatoires.
 *
 * <p>Catégories de sources restituées : {@value #CATEGORIE_AVIS} (avis de sécurité qui impactent un projet
 * visible, rapprochés par la stratégie du profil {@code developpeur}) et {@value #CATEGORIE_TEXTES} (fiches
 * réglementaires des espaces de l'appelant, validées par un expert métier, ADR-0018). Une fiche non validée
 * n'est jamais candidate, quel que soit le profil, et le garde-fou {@code VALIDATION_HUMAINE} d'un profil
 * s'applique en plus à tout élément qu'il range.
 */
@Path("/api/v1/me/veille")
@Tag(name = "Veille")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class VeilleProfilsResource {

    /** Avis de sécurité qui impacte un projet visible. */
    public static final String AVIS_SECURITE = "AVIS_SECURITE";
    /** Fiche d'analyse d'un texte officiel, validée par un expert métier. */
    public static final String FICHE_REGLEMENTAIRE = "FICHE_REGLEMENTAIRE";
    /** Catégorie de sources des avis de sécurité (manifestes, {@code categoriesSources}). */
    public static final String CATEGORIE_AVIS = "avis-securite";
    /** Catégorie de sources des textes officiels (manifestes, {@code categoriesSources}). */
    public static final String CATEGORIE_TEXTES = "textes-officiels";

    /** Nombre maximal d'impacts lus pour composer les avis de sécurité restitués. */
    static final int LIMITE_IMPACTS = 200;

    @Schema(name = "RubriqueElementVeille")
    public record RubriqueVue(String profil, String identifiant, String libelle) {
    }

    @Schema(name = "ProjetConcerne")
    public record ProjetVue(UUID projetId, String projet, String niveau) {
    }

    /**
     * Élément de veille restitué une seule fois. Le titre vient de la source : donnée externe non fiable,
     * à rendre comme texte.
     */
    @Schema(name = "ElementVeilleProfils")
    public record ElementVue(String type, UUID id, String source, String identifiant, String titre, String resume,
            String gravite, Instant date, LocalDate dateEffet, String lien, List<String> profils,
            List<RubriqueVue> rubriques, List<String> gardeFous, String mentionValidation, List<ProjetVue> projets) {
    }

    @Schema(name = "VeilleProfils")
    public record Veille(List<String> profilsActifs, List<ElementVue> elements) {
    }

    @Inject
    Access access;

    @Inject
    PreferencesService preferences;

    @Inject
    ResolutionPreferences resolution;

    @Inject
    CatalogueProfils catalogue;

    @Inject
    ImpactQueries impacts;

    @Inject
    SignalQueries signaux;

    @Inject
    FichesReglementaires fiches;

    @GET
    @Operation(summary = "Veille de l'appelant : union sans doublon des éléments de ses profils actifs, avec rubriques"
            + " et garde-fous")
    public Veille veille() {
        Caller caller = access.authenticated();
        List<String> profilsActifs = resolution.resoudre(preferences.lire(caller.accountId())).profilsActifs();
        List<ManifesteProfil> actifs = catalogue.publies().stream()
                .filter(m -> profilsActifs.contains(m.identifiant())).toList();
        Set<String> categories = new LinkedHashSet<>();
        for (ManifesteProfil m : actifs) {
            for (Rubrique r : m.rubriques()) {
                categories.addAll(r.categories());
            }
        }

        List<UnionProfils.Candidat<ElementVue>> candidats = new ArrayList<>();
        if (categories.contains(CATEGORIE_AVIS)) {
            candidats.addAll(avis(caller));
        }
        if (categories.contains(CATEGORIE_TEXTES)) {
            candidats.addAll(fichesValidees(caller));
        }
        candidats.sort(Comparator.comparing((UnionProfils.Candidat<ElementVue> c) -> c.contenu().date(),
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<ElementVue> elements = UnionProfils.unir(actifs, candidats).stream().map(r -> {
            ElementVue e = r.contenu();
            return new ElementVue(e.type(), e.id(), e.source(), e.identifiant(), e.titre(), e.resume(), e.gravite(),
                    e.date(), e.dateEffet(), e.lien(), r.profils(),
                    r.rubriques().stream().map(x -> new RubriqueVue(x.profil(), x.identifiant(), x.libelle())).toList(),
                    r.gardeFous(), e.mentionValidation(), e.projets());
        }).toList();
        return new Veille(actifs.stream().map(ManifesteProfil::identifiant).toList(), elements);
    }

    /** Avis de sécurité qui impactent un projet des espaces de l'appelant, avec ces seuls projets. */
    private List<UnionProfils.Candidat<ElementVue>> avis(Caller caller) {
        Map<UUID, List<ImpactQueries.ImpactView>> parSignal = new LinkedHashMap<>();
        for (ImpactQueries.ImpactView i : impacts.forWorkspaces(caller.accountId(), caller.memberWorkspaces(),
                LIMITE_IMPACTS)) {
            if (caller.memberOf(i.workspaceId())) {
                parSignal.computeIfAbsent(i.signalId(), k -> new ArrayList<>()).add(i);
            }
        }
        List<UnionProfils.Candidat<ElementVue>> candidats = new ArrayList<>();
        parSignal.forEach((signalId, vus) -> {
            Optional<SignalQueries.SignalView> s = signaux.find(signalId);
            ImpactQueries.ImpactView premier = vus.get(0);
            List<ProjetVue> projets = vus.stream()
                    .map(i -> new ProjetVue(i.projectId(), i.projectName(), i.level())).distinct().toList();
            ElementVue e = new ElementVue(AVIS_SECURITE, signalId, premier.signalSource(), premier.signalExternalId(),
                    premier.signalTitle(), null, s.map(SignalQueries.SignalView::severity).orElse(null),
                    s.map(v -> v.publishedAt() != null ? v.publishedAt() : v.modifiedAt()).orElse(premier.updatedAt()),
                    null, null, List.of(), List.of(), List.of(), null, projets);
            candidats.add(new UnionProfils.Candidat<>(AVIS_SECURITE + ":" + signalId, Set.of(CATEGORIE_AVIS), false, e));
        });
        return candidats;
    }

    /** Fiches réglementaires validées des espaces de l'appelant (lecture sous la RLS). */
    private List<UnionProfils.Candidat<ElementVue>> fichesValidees(Caller caller) {
        List<UnionProfils.Candidat<ElementVue>> candidats = new ArrayList<>();
        for (FichesReglementaires.Fiche f : fiches.fiches(caller.accountId(), caller.memberWorkspaces(), Optional.empty())) {
            // Garde-fou de la source (ADR-0018) : une fiche non validée n'est jamais candidate, quel que soit le profil.
            if (f.etat() != FichesReglementaires.Etat.VALIDEE || !caller.memberOf(f.espaceId())) {
                continue;
            }
            ElementVue e = new ElementVue(FICHE_REGLEMENTAIRE, f.id(), f.source(), f.referenceOfficielle(),
                    f.titreOfficiel(), f.resume(), null,
                    f.publication() == null ? null : f.publication().atStartOfDay(ZoneOffset.UTC).toInstant(),
                    f.effet(), lienHttp(f.lienOfficiel()), List.of(), List.of(), List.of(), FicheAnalyse.MENTION_VALIDEE,
                    List.of());
            candidats.add(new UnionProfils.Candidat<>(FICHE_REGLEMENTAIRE + ":" + f.id(), Set.of(CATEGORIE_TEXTES), true, e));
        }
        return candidats;
    }

    /** Lien restituable : seules les adresses absolues http et https le sont (XSS). */
    static String lienHttp(String lien) {
        if (lien == null) {
            return null;
        }
        String l = lien.strip();
        return l.regionMatches(true, 0, "https://", 0, 8) || l.regionMatches(true, 0, "http://", 0, 7) ? l : null;
    }
}
