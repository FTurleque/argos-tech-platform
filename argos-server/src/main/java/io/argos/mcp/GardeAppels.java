package io.argos.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.api.security.Caller;
import io.argos.api.security.CallerResolver;
import io.argos.audit.JournalAppelsMcp;
import io.argos.audit.JournalAppelsMcp.Issue;
import io.argos.audit.JournalRefus;
import io.argos.workspace.AccesRefuse;
import io.argos.workspace.InscriptionRefusee;
import io.argos.workspace.Introuvable;
import io.quarkiverse.mcp.server.ToolCallException;

/**
 * Chaîne commune à tous les outils MCP (diagramme DC-1) : appelant établi comme pour le portail,
 * limitation de débit, exécution, journal de l'appel et traduction des refus.
 *
 * <ul>
 *   <li>Le jeton OIDC est déjà validé par Quarkus avant l'arrivée de la requête : un appel sans
 *       jeton valide reçoit 401 sans atteindre l'outil (MCP-03-S1). L'appelant et ses espaces sont
 *       établis par le même {@link CallerResolver} que l'API REST (ADR-0014, ADR-0016).</li>
 *   <li>Une limite atteinte refuse l'appel avec l'erreur {@code LIMITE_DEBIT}, journalisée dans le
 *       journal des refus et dans celui des appels (MCP-06-S1). La limite s'applique après
 *       l'établissement de l'appelant, car le refus est journalisé avec son compte.</li>
 *   <li>Une ressource d'un autre espace est refusée comme introuvable, sans rien révéler, et le refus
 *       est journalisé (MCP-04, ACC-06).</li>
 *   <li>Une erreur inattendue ne renvoie aucune donnée partielle.</li>
 * </ul>
 */
@ApplicationScoped
public class GardeAppels {

    private static final Logger LOG = Logger.getLogger(GardeAppels.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int LONGUEUR_MAX_PARAMETRES = 2000;

    @Inject
    CallerResolver appelants;

    @Inject
    LimiteurDebit limiteur;

    @Inject
    JournalAppelsMcp journalAppels;

    @Inject
    JournalRefus journalRefus;

    @Inject
    JsonWebToken jeton;

    /**
     * Exécute un outil pour l'appelant courant.
     *
     * @param outil      nom de l'outil MCP
     * @param parametres paramètres reçus, journalisés (bornés)
     * @param travail    lecture métier pour l'appelant
     */
    public <T> T appeler(String outil, Map<String, ?> parametres, Function<Caller, T> travail) {
        long debut = System.nanoTime();
        String parametresJson = json(parametres);
        Caller appelant;
        try {
            appelant = appelants.current().orElseThrow(() -> new ToolCallException("NON_AUTHENTIFIE : jeton absent ou invalide."));
        } catch (InscriptionRefusee e) {
            journalRefus.enregistrer(null, sujet(), ressource(outil), "INSCRIPTION_SUR_INVITATION");
            journalAppels.enregistrer(null, sujet(), outil, parametresJson, ms(debut), Issue.REFUS_ACCES);
            throw new ToolCallException("NON_AUTORISE : aucun compte Argos n'est associé à ce jeton.");
        }
        UUID compte = appelant.accountId();
        if (!limiteur.admettre(compte)) {
            journalRefus.enregistrer(compte, null, ressource(outil), "LIMITE_DEBIT");
            journalAppels.enregistrer(compte, null, outil, parametresJson, ms(debut), Issue.REFUS_LIMITE);
            throw new ToolCallException("LIMITE_DEBIT : nombre maximal d'appels par minute atteint, réessayez plus tard.");
        }
        try {
            T resultat = travail.apply(appelant);
            journalAppels.enregistrer(compte, null, outil, parametresJson, ms(debut), Issue.SUCCES);
            return resultat;
        } catch (ParametreInvalide e) {
            journalAppels.enregistrer(compte, null, outil, parametresJson, ms(debut), Issue.PARAMETRES_INVALIDES);
            throw new ToolCallException("PARAMETRES_INVALIDES : " + e.getMessage());
        } catch (AccesRefuse e) {
            journalRefus.enregistrer(compte, null, ressource(outil), e.motif().name());
            journalAppels.enregistrer(compte, null, outil, parametresJson, ms(debut), Issue.REFUS_ACCES);
            throw new ToolCallException("INTROUVABLE : ressource introuvable dans votre périmètre.");
        } catch (Introuvable e) {
            // Ressource inexistante : même réponse qu'un refus, sans entrée au journal des refus.
            journalAppels.enregistrer(compte, null, outil, parametresJson, ms(debut), Issue.INTROUVABLE);
            throw new ToolCallException("INTROUVABLE : ressource introuvable dans votre périmètre.");
        } catch (RuntimeException e) {
            LOG.errorf(e, "Échec de l'outil MCP %s", outil);
            journalAppels.enregistrer(compte, null, outil, parametresJson, ms(debut), Issue.ERREUR);
            throw new ToolCallException("INDISPONIBLE : Argos ne peut pas répondre pour le moment ; aucune donnée partielle n'est renvoyée.");
        }
    }

    /** Ressource inscrite au journal des refus : protocole et outil, sans paramètre (ACC-06). */
    static String ressource(String outil) {
        return "MCP tools/call " + outil;
    }

    private String sujet() {
        return Optional.ofNullable(jeton).map(JsonWebToken::getSubject).orElse(null);
    }

    private static long ms(long debutNanos) {
        return (System.nanoTime() - debutNanos) / 1_000_000;
    }

    /**
     * Paramètres sérialisés en JSON, bornés à 2 000 caractères. Chaque texte est tronqué avant la
     * sérialisation : le coût reste borné quelle que soit la taille reçue.
     */
    static String json(Map<String, ?> parametres) {
        Map<String, Object> copie = new LinkedHashMap<>();
        parametres.forEach((k, v) -> {
            if (v instanceof CharSequence texte && texte.length() > LONGUEUR_MAX_PARAMETRES) {
                copie.put(k, texte.subSequence(0, LONGUEUR_MAX_PARAMETRES).toString());
            } else if (v != null) {
                copie.put(k, v);
            }
        });
        try {
            String texte = JSON.writeValueAsString(copie);
            if (texte.length() <= LONGUEUR_MAX_PARAMETRES) {
                return texte;
            }
            return JSON.writeValueAsString(Map.of("tronque", true, "extrait", texte.substring(0, LONGUEUR_MAX_PARAMETRES)));
        } catch (JsonProcessingException e) {
            return "{\"illisible\":true}";
        }
    }
}
