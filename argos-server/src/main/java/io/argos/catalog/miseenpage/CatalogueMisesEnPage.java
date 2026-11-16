package io.argos.catalog.miseenpage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Catalogue des mises en page (ADR-0023, exigences AFF-01 et AFF-04).
 *
 * <p>Les mises en page déclarées viennent de la ressource {@value #RESSOURCE}.
 * Une mise en page listée dans {@code argos.catalogue.mises-en-page.retirees}
 * est retirée : elle n'est plus publiée et ne peut plus être choisie.
 *
 * <p>Le catalogue est validé au démarrage avec les mêmes contrôles que le catalogue des profils :
 * propriété inconnue, identifiant absent, mal formé (plus de 64 caractères compris) ou déclaré deux
 * fois, libellé absent : le démarrage échoue avec un message explicite.
 *
 * <p>Ne connaît pas les préférences des utilisateurs (conception, composant {@code catalog}).
 */
@ApplicationScoped
public class CatalogueMisesEnPage {

    /** Mise en page appliquée sans choix ou après un retrait (AFF-04). */
    public static final String PAR_DEFAUT = "classique";

    static final String RESSOURCE = "catalog/mises-en-page.json";

    /** Même forme que les identifiants de profils ; 64 caractères au plus (colonne mise_en_page). */
    private static final Pattern IDENTIFIANT = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final int LONGUEUR_MAX = 64;

    private final List<MiseEnPage> declarees;
    private final Set<String> retirees;

    @Inject
    public CatalogueMisesEnPage(ObjectMapper mapper,
            @ConfigProperty(name = "argos.catalogue.mises-en-page.retirees") Optional<Set<String>> retirees) {
        this(lire(mapper), retirees.orElse(Set.of()));
    }

    CatalogueMisesEnPage(List<MiseEnPage> declarees, Set<String> retirees) {
        valider(declarees);
        if (retirees.contains(PAR_DEFAUT)) {
            throw new IllegalStateException("La mise en page par défaut « " + PAR_DEFAUT + " » ne peut pas être retirée");
        }
        if (declarees.stream().noneMatch(m -> m.identifiant().equals(PAR_DEFAUT))) {
            throw new IllegalStateException("Le catalogue doit déclarer la mise en page « " + PAR_DEFAUT + " »");
        }
        this.declarees = List.copyOf(declarees);
        this.retirees = Set.copyOf(retirees);
    }

    /** Mises en page publiées, dans l'ordre du catalogue. */
    public List<MiseEnPage> publiees() {
        return declarees.stream().filter(m -> !retirees.contains(m.identifiant())).toList();
    }

    /** Vrai si la mise en page est déclarée et non retirée. */
    public boolean estPubliee(String identifiant) {
        return identifiant != null && publiees().stream().anyMatch(m -> m.identifiant().equals(identifiant));
    }

    private static void valider(List<MiseEnPage> declarees) {
        List<String> erreurs = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        for (MiseEnPage m : declarees) {
            if (m == null || m.identifiant() == null || m.identifiant().length() > LONGUEUR_MAX
                    || !IDENTIFIANT.matcher(m.identifiant()).matches()) {
                erreurs.add("identifiant absent ou mal formé : " + (m == null ? null : m.identifiant()));
                continue;
            }
            if (!vus.add(m.identifiant())) {
                erreurs.add("mise en page déclarée deux fois : " + m.identifiant());
            }
            if (m.libelle() == null || m.libelle().isBlank()) {
                erreurs.add("libellé absent : " + m.identifiant());
            }
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalStateException("Catalogue des mises en page invalide : " + String.join(" ; ", erreurs));
        }
    }

    private static List<MiseEnPage> lire(ObjectMapper mapper) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESSOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Ressource absente : " + RESSOURCE);
            }
            return analyser(mapper, in);
        } catch (IOException e) {
            throw new UncheckedIOException("Catalogue des mises en page illisible : " + RESSOURCE, e);
        }
    }

    /** Lecture stricte : une propriété inconnue est refusée, comme pour les manifestes de profils. */
    static List<MiseEnPage> analyser(ObjectMapper mapper, InputStream in) throws IOException {
        return mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(in, new TypeReference<List<MiseEnPage>>() { });
    }
}
