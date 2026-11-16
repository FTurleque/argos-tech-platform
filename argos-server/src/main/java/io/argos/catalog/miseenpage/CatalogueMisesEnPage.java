package io.argos.catalog.miseenpage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Catalogue des mises en page (ADR-0023, exigences AFF-01 et AFF-04).
 *
 * <p>Les mises en page déclarées viennent de la ressource {@value #RESSOURCE}.
 * Une mise en page listée dans {@code argos.catalogue.mises-en-page.retirees}
 * est retirée : elle n'est plus publiée et ne peut plus être choisie.
 *
 * <p>Ne connaît pas les préférences des utilisateurs (conception, composant {@code catalog}).
 */
@ApplicationScoped
public class CatalogueMisesEnPage {

    /** Mise en page appliquée sans choix ou après un retrait (AFF-04). */
    public static final String PAR_DEFAUT = "classique";

    static final String RESSOURCE = "catalog/mises-en-page.json";

    private final List<MiseEnPage> declarees;
    private final Set<String> retirees;

    @Inject
    public CatalogueMisesEnPage(ObjectMapper mapper,
            @ConfigProperty(name = "argos.catalogue.mises-en-page.retirees") Optional<Set<String>> retirees) {
        this(lire(mapper), retirees.orElse(Set.of()));
    }

    CatalogueMisesEnPage(List<MiseEnPage> declarees, Set<String> retirees) {
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

    private static List<MiseEnPage> lire(ObjectMapper mapper) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESSOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Ressource absente : " + RESSOURCE);
            }
            return mapper.readValue(in, new TypeReference<List<MiseEnPage>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException("Catalogue des mises en page illisible : " + RESSOURCE, e);
        }
    }
}
