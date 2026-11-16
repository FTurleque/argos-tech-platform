package io.argos.aigateway;

/**
 * Classe de confidentialité d'une entrée transmise à la passerelle (IAG-02, arc42 §10.2 Q-02).
 *
 * <p>Seules {@link #PUBLIC} (avis publiés, textes de veille) et {@link #INTERNAL} (métadonnées de
 * composants et de projets) sortent vers le modèle (Q-4) ; une demande qui contient une entrée
 * {@link #CONFIDENTIAL} ou {@link #RESTRICTED} est refusée en entier.
 */
public enum ClasseDonnee {
    PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED;

    /** Vrai si la classe peut être transmise au modèle. */
    public boolean transmissible() {
        return this == PUBLIC || this == INTERNAL;
    }
}
