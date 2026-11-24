package io.argos.observabilite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Profil des tests d'observabilité : journal fichier ({@link #JOURNAL}) au format JSON de production, avec le
 * filtre des jetons ; planificateur actif avec la seule tâche d'essai (toutes les tâches de production désactivées
 * par {@code off}) ; retard toléré nul pour observer l'état « en retard » ; passerelle IA désactivée.
 */
public class ProfilObservabilite implements QuarkusTestProfile {

    /** Fichier du journal JSON, relatif au répertoire du module. */
    public static final String JOURNAL = "target/journaux-essai/argos.json";

    /** Propriétés de planification des tâches de production, toutes désactivées dans ce profil. */
    static final List<String> PLANIFICATIONS = List.of("argos.ingestion.planification.cadence",
            "argos.inventory.import.cron", "argos.alerting.resume-quotidien.cron",
            "argos.alerting.resume-hebdomadaire.cron", "argos.alerting.envoi.intervalle", "argos.veille-web.cadence",
            "argos.radar.tache.cron", "argos.activite.cadence", "argos.activite.purge.cron",
            "argos.profil-artiste.collecte.cron", "argos.profil-artiste.rappels.cron",
            "argos.reglementaire.collecte.cron", "argos.reglementaire.rappels.cron");

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> configuration = new HashMap<>();
        PLANIFICATIONS.forEach(propriete -> configuration.put(propriete, "off"));
        configuration.put("quarkus.scheduler.enabled", "true");
        configuration.put("argos.essai.tache-observee.cadence", "1s");
        configuration.put("argos.observabilite.taches.facteur-retard", "0");
        configuration.put("argos.ia.active", "false");
        configuration.put("quarkus.log.file.enabled", "true");
        configuration.put("quarkus.log.file.path", JOURNAL);
        configuration.put("quarkus.log.file.json.enabled", "true");
        configuration.put("quarkus.log.file.filter", FiltreJetons.NOM);
        return configuration;
    }
}
