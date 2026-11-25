package io.argos.mcp;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import io.argos.commun.securite.LimiteurDebit;
import io.argos.audit.JournalRefus;
import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.smallrye.mutiny.Uni;

/**
 * Limite le débit des négociations {@code initialize} (MCP-06). Un client qui négocie une version de
 * protocole antérieure à 2026-07-28 ouvre une session en mémoire (voir la fiche {@code serveur-mcp},
 * « Écarts constatés ») ; la limite des appels d'outils de {@link GardeAppels} ne la couvre pas.
 *
 * <p>La limite est celle des appels d'outils ({@code argos.mcp.debit.requetes-par-minute}), comptée
 * séparément, par sujet du jeton OIDC : la requête est déjà authentifiée (401 sans jeton valide) mais
 * aucun compte Argos n'est exigé pour négocier. Un refus est inscrit au journal des refus avec le
 * motif {@code LIMITE_DEBIT}. Les sessions inactives sont fermées par l'extension au bout de
 * {@code quarkus.mcp.server.connection-idle-timeout}.
 */
@ApplicationScoped
public class ControleInitialisation implements InitialCheck {

    /** Ressource inscrite au journal des refus pour une négociation refusée. */
    static final String RESSOURCE = "MCP initialize";

    @ConfigProperty(name = "argos.mcp.debit.requetes-par-minute", defaultValue = "60")
    int requetesParMinute;

    @Inject
    JsonWebToken jeton;

    @Inject
    JournalRefus journalRefus;

    LimiteurDebit limiteur;

    @PostConstruct
    void initialiser() {
        limiteur = new LimiteurDebit(requetesParMinute);
    }

    @Override
    public Uni<CheckResult> perform(InitialRequest requete) {
        String sujet = Optional.ofNullable(jeton).map(JsonWebToken::getSubject).orElse(null);
        if (sujet == null) {
            return CheckResult.error("NON_AUTHENTIFIE : jeton absent ou invalide.");
        }
        UUID cle = UUID.nameUUIDFromBytes(("oidc:" + sujet).getBytes(StandardCharsets.UTF_8));
        if (!limiteur.admettre(cle)) {
            journalRefus.enregistrer(null, sujet, RESSOURCE, "LIMITE_DEBIT");
            return CheckResult.error("LIMITE_DEBIT : nombre maximal de négociations par minute atteint, réessayez plus tard.");
        }
        return CheckResult.success();
    }
}
