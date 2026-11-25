package io.argos.api.recherche;

import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.api.security.LimiteDebitAtteinte;
import io.argos.api.security.LimiteurDebit;

/**
 * Limite de débit par compte de la recherche plein texte et du rappel d'une vue enregistrée (RCH-06,
 * {@code argos.recherche.debit.requetes-par-minute}) : {@code GET /api/v1/recherche} et
 * {@code GET /api/v1/vues/{id}/resultats} partagent un seul compteur, puisqu'une vue rejoue la même recherche.
 */
@ApplicationScoped
public class LimiteRecherche {

    @ConfigProperty(name = "argos.recherche.debit.requetes-par-minute", defaultValue = "60")
    int requetesParMinute;

    LimiteurDebit limiteur;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(requetesParMinute);
    }

    /** Compte l'appel du compte ; refus journalisé et 429 par la couche d'exposition si la limite est atteinte. */
    public void admettre(UUID compte) {
        if (!limiteur.admettre(compte)) {
            throw new LimiteDebitAtteinte();
        }
    }
}
