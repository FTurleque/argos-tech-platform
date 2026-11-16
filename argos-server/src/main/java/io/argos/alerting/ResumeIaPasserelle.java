package io.argos.alerting;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.aigateway.DemandeIa;
import io.argos.aigateway.EntreeIa;
import io.argos.aigateway.PasserelleIa;
import io.argos.aigateway.RegistrePrompts;
import io.argos.aigateway.ReponseIa;
import io.quarkus.arc.DefaultBean;

/**
 * Résumé IA d'une alerte par la passerelle {@code ai-gateway} (IAG-01-S1, tâche {@code resume-alerte}).
 *
 * <p>Seul le titre du signal, donnée publique, est transmis (classe PUBLIC). L'appel est imputé au
 * premier des espaces concernés dont le quota n'est pas épuisé ; si tous le sont, ou si le plafond de
 * l'instance est atteint, aucun appel n'est fait et l'alerte part sans résumé ({@code PLAFOND_ATTEINT},
 * IAG-03-S1). Une sortie refusée ou non conforme donne l'état {@code REFUSE} ; un fournisseur
 * injoignable ou une IA désactivée donne {@code INDISPONIBLE}.
 *
 * <p>Bean par défaut : un autre fournisseur de {@link ResumeIa} le remplace (simulations de test).
 */
@ApplicationScoped
@DefaultBean
public class ResumeIaPasserelle implements ResumeIa {

    @Inject
    PasserelleIa passerelle;

    @Override
    public boolean budgetDisponible() {
        return passerelle.disponible(null);
    }

    @Override
    public boolean budgetDisponible(Set<UUID> espaces) {
        return espaces.isEmpty() ? passerelle.disponible(null) : espaces.stream().anyMatch(passerelle::disponible);
    }

    @Override
    public String resumer(UUID signalId, String titre) {
        return demander(titre, null);
    }

    @Override
    public String resumer(UUID signalId, String titre, Set<UUID> espaces) {
        List<UUID> tries = espaces.stream().sorted(Comparator.comparing(UUID::toString)).toList();
        UUID impute = tries.stream().filter(passerelle::disponible).findFirst()
                .orElse(tries.isEmpty() ? null : tries.get(0));
        return demander(titre, impute);
    }

    private String demander(String titre, UUID espaceId) {
        ReponseIa r = passerelle.demander(new DemandeIa(RegistrePrompts.RESUME_ALERTE, espaceId,
                List.of(EntreeIa.publique("titre", titre)), List.of()));
        return switch (r.issue()) {
            case PRODUIT -> r.champ("resume");
            case INDISPONIBLE, DESACTIVEE -> throw new IllegalStateException("passerelle IA : " + r.issue());
            default -> "";
        };
    }
}
