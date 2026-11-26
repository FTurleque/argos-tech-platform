package io.argos.conservation;

import java.util.Arrays;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.commun.Jdbc;

/**
 * Suppression par lots des lignes échues (ADR-0041) : chaque lot est une instruction autonome, validée hors
 * transaction JTA, ce qui borne la durée des verrous et le volume de chaque instruction. Le SQL fourni par la règle se
 * termine par un paramètre {@code LIMIT ?}, lié ici à la taille du lot.
 */
@ApplicationScoped
public class SuppressionParLots {

    @Inject
    Jdbc jdbc;

    @ConfigProperty(name = "argos.conservation.taille-lot", defaultValue = "5000")
    int tailleLot;

    /**
     * Exécute {@code sql} jusqu'à ce qu'un lot supprime moins de lignes que la taille du lot.
     *
     * @param sql suppression dont le dernier paramètre est la taille du lot ({@code … LIMIT ?})
     * @param parametres paramètres positionnels qui précèdent la taille du lot
     * @return nombre total de lignes supprimées
     */
    public long supprimer(String sql, Object... parametres) {
        int taille = Math.max(tailleLot, 1);
        Object[] avecLot = Arrays.copyOf(parametres, parametres.length + 1);
        avecLot[parametres.length] = taille;
        long total = 0;
        int supprimees;
        do {
            supprimees = jdbc.modifier(sql, Jdbc.parametres(avecLot));
            total += supprimees;
        } while (supprimees >= taille);
        return total;
    }
}
