package io.argos.alerting;

import java.util.UUID;

/**
 * Liens des alertes, des résumés et des rappels vers les vues de l'IHM (ALR-09). L'IHM utilise l'historique
 * « hash » : chaque lien est l'adresse publique de la plateforme suivie d'un gabarit {@code /#/…} dont
 * {@code {id}} est remplacé par l'identifiant de l'objet. Le test {@code argos-ui/tests/liens-alertes.test.mjs}
 * vérifie que chaque gabarit déclaré ici correspond à une route de l'IHM.
 */
final class LiensIhm {

    /** Vue d'un signal. */
    static final String SIGNAL = "/#/signaux/{id}";

    /** Vue radar d'un espace. */
    static final String RADAR_ESPACE = "/#/espaces/{id}/radar";

    /** Opportunité du profil artiste. */
    static final String OPPORTUNITE = "/#/profils/artiste/opportunites/{id}";

    private static final String PARAMETRE = "{id}";

    private LiensIhm() {
    }

    /**
     * Lien complet : adresse publique, sans barre oblique finale, suivie du gabarit renseigné.
     *
     * @param adressePublique adresse publique de la plateforme ({@code argos.alerting.lien-base})
     * @param gabarit l'un des gabarits de cette classe
     * @param id identifiant de l'objet visé
     */
    static String lien(String adressePublique, String gabarit, UUID id) {
        return adressePublique.replaceAll("/+$", "") + gabarit.replace(PARAMETRE, id.toString());
    }
}
