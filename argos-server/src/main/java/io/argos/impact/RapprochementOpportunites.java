package io.argos.impact;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.catalog.profil.StrategieDeclaree;
import io.argos.signal.opportunite.Opportunite;
import io.argos.signal.opportunite.Opportunites;

/**
 * Restitution des opportunités du profil {@code artiste} à partir des disciplines et territoires
 * suivis (ART-03, DC-1). Exécute la stratégie {@link StrategieDisciplineTerritoire} déclarée par le
 * manifeste ; si le profil est retiré du catalogue ou ne déclare plus la stratégie, rien n'est restitué.
 *
 * <p>Les suivis sont fournis par l'appelant ({@code api} ou {@code alerting}) : ce module ne lit pas les
 * préférences des utilisateurs. Les opportunités sont des données publiques : la restitution n'élargit
 * aucune donnée visible (PRF-05).
 */
@ApplicationScoped
public class RapprochementOpportunites {

    /** Identifiant du profil servi. */
    public static final String PROFIL = "artiste";

    /** Stratégies de ce module, confrontées aux manifestes par les tests. */
    public static final Set<String> STRATEGIES_IMPLEMENTEES = Set.of(StrategieDisciplineTerritoire.IDENTIFIANT);

    /**
     * Opportunité restituée, avec la décision explicable et le résumé sans avis d'éligibilité ; ses
     * champs sont recopiés pour que les modules appelants ne dépendent pas de {@code signal}.
     */
    public record Restitution(UUID id, String rubrique, String titre, String organisme, String sourceLibelle,
            String lien, List<String> disciplines, List<String> territoires, LocalDate dateLimite,
            StrategieDisciplineTerritoire.Decision decision, ResumeOpportunite.Resume resume) {

        public Restitution {
            disciplines = List.copyOf(disciplines);
            territoires = List.copyOf(territoires);
        }

        static Restitution de(Opportunite o, StrategieDisciplineTerritoire.Decision d) {
            ResumeOpportunite.Resume r = ResumeOpportunite.de(o);
            return new Restitution(o.id(), o.rubrique(), o.titre(), r.organisme(), o.sourceLibelle(), o.lien(),
                    o.disciplines(), o.territoires(), o.dateLimite(), d, r);
        }
    }

    @Inject
    CatalogueProfils catalogue;

    @Inject
    Opportunites opportunites;

    /** Vrai si le profil est publié et déclare la stratégie de ce module. */
    public boolean actif() {
        Optional<ManifesteProfil> m = catalogue.trouver(PROFIL);
        return m.isPresent() && m.get().strategies().stream().map(StrategieDeclaree::identifiant)
                .anyMatch(StrategieDisciplineTerritoire.IDENTIFIANT::equals);
    }

    /** Opportunités restituées pour ces suivis, date limite la plus proche d'abord. */
    public List<Restitution> restituer(Set<String> disciplines, Set<String> territoires, LocalDate aujourdhui) {
        if (!actif() || disciplines.isEmpty() || territoires.isEmpty()) {
            return List.of();
        }
        List<Restitution> resultat = new ArrayList<>();
        for (Opportunite o : opportunites.candidates(PROFIL, List.copyOf(disciplines), aujourdhui)) {
            StrategieDisciplineTerritoire.Decision d = StrategieDisciplineTerritoire.evaluer(o, disciplines, territoires,
                    aujourdhui);
            if (d.issue().restituee()) {
                resultat.add(Restitution.de(o, d));
            }
        }
        return resultat;
    }

    /** Résumé d'une opportunité du profil (ART-06-S1). */
    public Optional<ResumeOpportunite.Resume> resume(UUID id) {
        if (!actif()) {
            return Optional.empty();
        }
        return opportunites.trouver(PROFIL, id).map(ResumeOpportunite::de);
    }
}
