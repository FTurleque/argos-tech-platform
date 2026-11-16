package io.argos.testsupport;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.catalog.standard.ReferentielStandards;
import io.argos.regintel.FichesReglementaires;
import io.argos.releaseradar.CalendrierSorties;

/**
 * Référentiel des standards, calendrier des sorties et fiches réglementaires simulés, en
 * remplacement des implémentations vides par défaut, tant que les changements
 * {@code construire-radar-et-standards} et {@code suivre-veille-reglementaire} ne sont pas livrés.
 * Chaque simulation respecte le contrat de son point d'extension (filtre par espaces demandés) et
 * est vidée par {@link #vider()}.
 */
public final class ReferentielsSimules {

    private ReferentielsSimules() {
    }

    /** Vide toutes les simulations. */
    public static void vider() {
        StandardsSimules.DONNEES.clear();
        CalendrierSimule.DONNEES.clear();
        FichesSimulees.DONNEES.clear();
    }

    @ApplicationScoped
    public static class StandardsSimules implements ReferentielStandards {

        static final List<Standard> DONNEES = new CopyOnWriteArrayList<>();

        public static void ajouter(Standard standard) {
            DONNEES.add(standard);
        }

        @Override
        public List<Standard> standards(String cle, Set<UUID> espaces) {
            return DONNEES.stream().filter(s -> s.cle().equals(cle) && espaces.contains(s.espaceId())).toList();
        }
    }

    @ApplicationScoped
    public static class CalendrierSimule implements CalendrierSorties {

        static final List<Echeance> DONNEES = new CopyOnWriteArrayList<>();

        public static void ajouter(Echeance echeance) {
            DONNEES.add(echeance);
        }

        @Override
        public List<Echeance> echeances(Collection<String> cles, LocalDate depuis, LocalDate jusqua) {
            return DONNEES.stream()
                    .filter(e -> cles.contains(e.cle()) && !e.date().isBefore(depuis) && !e.date().isAfter(jusqua))
                    .sorted(java.util.Comparator.comparing(Echeance::date)).toList();
        }

        @Override
        public Optional<Echeance> finDeSupport(String cle, String version) {
            return DONNEES.stream().filter(e -> e.nature() == Nature.FIN_DE_SUPPORT && e.cle().equals(cle)
                    && e.version().equals(version)).findFirst();
        }
    }

    @ApplicationScoped
    public static class FichesSimulees implements FichesReglementaires {

        static final List<Fiche> DONNEES = new CopyOnWriteArrayList<>();

        public static void ajouter(Fiche fiche) {
            DONNEES.add(fiche);
        }

        @Override
        public List<Fiche> fiches(Set<UUID> espaces, Optional<String> domaine) {
            return DONNEES.stream().filter(f -> espaces.contains(f.espaceId())
                    && domaine.map(d -> d.equals(f.domaine())).orElse(true)).toList();
        }
    }
}
