package io.argos.testsupport;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.regintel.FichesReglementaires;

/**
 * Fiches réglementaires simulées, en remplacement de l'implémentation vide par défaut, tant que le
 * changement {@code suivre-veille-reglementaire} n'est pas livré. La simulation respecte le contrat de
 * son point d'extension (filtre par espaces demandés) et est vidée par {@link #vider()}. Le référentiel
 * des standards et le calendrier des sorties sont réels (changement {@code construire-radar-et-standards}) :
 * voir {@link RadarFixtures}.
 */
public final class ReferentielsSimules {

    private ReferentielsSimules() {
    }

    /** Vide toutes les simulations. */
    public static void vider() {
        FichesSimulees.DONNEES.clear();
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
