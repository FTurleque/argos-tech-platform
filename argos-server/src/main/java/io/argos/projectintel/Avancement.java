package io.argos.projectintel;

import java.util.Optional;

/**
 * Avancement d'un jalon avec son dénominateur explicite (PIN-02) : « 27 sur 30 (90 %) ».
 *
 * <p>Le pourcentage est calculé par le serveur, jamais par l'IA, et arrondi à l'entier inférieur :
 * il n'atteint 100 % que lorsque tous les tickets du jalon sont fermés. Sans dénominateur (aucun
 * ticket dans le jalon) ou avec des chiffres incohérents, aucun avancement n'existe.
 *
 * @param fermes tickets fermés du jalon
 * @param total tickets du jalon, dénominateur explicite
 * @param pourcentage {@code fermes * 100 / total}, arrondi à l'entier inférieur
 * @param libelle forme affichée, dénominateur compris
 */
public record Avancement(int fermes, int total, int pourcentage, String libelle) {

    /** Avancement de {@code fermes} sur {@code total} ; vide sans dénominateur strictement positif. */
    public static Optional<Avancement> de(int fermes, int total) {
        if (total <= 0 || fermes < 0 || fermes > total) {
            return Optional.empty();
        }
        int pourcentage = (int) ((fermes * 100L) / total);
        return Optional.of(new Avancement(fermes, total, pourcentage,
                fermes + " sur " + total + " (" + pourcentage + " %)"));
    }
}
