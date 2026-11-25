package io.argos.impact;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.commun.ContexteBase;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;
import io.argos.signal.web.ActualiteRecue;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;

/**
 * Rapprochement d'une actualité web avec les projets par nom de technologie (WEB-06, ADR-0013) :
 * toujours au niveau {@code A_VERIFIER}, jamais {@code EXACT} ni {@code PROBABLE}, sans impact
 * projet ({@code project_impact}) ni alerte.
 *
 * <p>Seules les actualités citées par une source d'espace sont rapprochées, et seulement des
 * projets de cet espace : une source personnelle ne révèle rien aux autres membres
 * ([PROPOSITION] du changement {@code collecter-veille-rss-web}). Le contenu lu est celui de
 * l'actualité de cet espace, alimentée par ses seules sources : une source d'un autre périmètre ne
 * peut ni fixer ce contenu ni provoquer un rapprochement dans l'espace. La clé composée
 * {@code actualite_impact (actualite_id, workspace_id) → actualite (id, espace_id)} de la migration
 * V10 l'impose aussi en base. Le nom d'une technologie est
 * déduit de la clé de paquet des composants inventoriés ({@link #terme(String)}) et recherché comme
 * mot entier, sans tenir compte de la casse, dans le titre et le texte de l'actualité.
 */
@ApplicationScoped
public class RapprochementActualites {

    private static final Logger LOG = Logger.getLogger(RapprochementActualites.class);

    /** Méthode enregistrée avec chaque rapprochement. */
    public static final String METHODE = "NOM_TECHNOLOGIE_V1";
    static final int TERME_MIN = 3;

    /** Rapprochement visible d'un membre de l'espace. */
    public record Rapprochement(UUID id, UUID actualiteId, UUID projetId, String projetNom, UUID espaceId,
            String technologie, String niveau, String methode, Instant creeLe) {
    }

    /** Paquet d'un projet tel que lu dans l'inventaire. */
    private record PaquetDeProjet(UUID projet, String cle) {
    }

    @Inject
    Jdbc jdbc;

    @Inject
    ContexteBase contexteBase;

    void surActualiteRecue(@Observes(during = TransactionPhase.AFTER_SUCCESS) ActualiteRecue evenement) {
        if (evenement.espaceId() == null) {
            return;
        }
        try {
            QuarkusTransaction.requiringNew().run(() -> rapprocher(evenement.actualiteId(), evenement.espaceId()));
        } catch (IllegalStateException | QuarkusTransactionException erreur) {
            LOG.errorf(erreur, "rapprochement de l'actualité %s en échec", evenement.actualiteId());
        }
    }

    /** Rapproche une actualité des projets d'un espace ; renvoie le nombre de rapprochements créés. */
    public int rapprocher(UUID actualiteId, UUID espaceId) {
        Optional<String> contenu = jdbc.unique("SELECT titre || ' ' || texte FROM actualite WHERE id = ? AND espace_id = ?",
                Jdbc.parametres(actualiteId, espaceId), resultat -> resultat.getString(1));
        if (contenu.isEmpty()) {
            return 0;
        }
        Map<UUID, Set<String>> termesParProjet = new LinkedHashMap<>();
        jdbc.lister("""
                SELECT DISTINCT p.id, c.package_key FROM component c JOIN project p ON p.id = c.project_id
                WHERE p.workspace_id = ? ORDER BY p.id, c.package_key
                """, Jdbc.parametres(espaceId),
                resultat -> new PaquetDeProjet(resultat.getObject(1, UUID.class), resultat.getString(2)))
                .forEach(paquet -> terme(paquet.cle()).ifPresent(terme ->
                        termesParProjet.computeIfAbsent(paquet.projet(), cleProjet -> new TreeSet<>()).add(terme)));
        int crees = 0;
        for (Map.Entry<UUID, Set<String>> entree : termesParProjet.entrySet()) {
            for (String terme : entree.getValue()) {
                if (!mentionne(contenu.get(), terme)) {
                    continue;
                }
                crees += jdbc.modifier("""
                        INSERT INTO actualite_impact (id, actualite_id, project_id, workspace_id, technologie, niveau,
                                                      methode, cree_le)
                        VALUES (?, ?, ?, ?, ?, 'A_VERIFIER', ?, now())
                        ON CONFLICT (actualite_id, project_id, technologie) DO NOTHING
                        """, Jdbc.parametres(UUID.randomUUID(), actualiteId, entree.getKey(), espaceId, terme, METHODE));
            }
        }
        return crees;
    }

    /**
     * Rapprochements visibles du compte pour une actualité et pour les actualités de même empreinte
     * des autres périmètres qu'il voit (RLS sous {@code argos_app} : espaces dont il est membre,
     * actualités de son périmètre citées par une source qu'il voit) : un article restitué une fois
     * montre les rapprochements de tous ses espaces visibles.
     */
    public List<Rapprochement> pour(UUID actualiteId, UUID compteId) {
        return contexteBase.executer(ContexteBase.Contexte.duCompte(compteId), connexion -> Jdbc.lister(connexion, """
                SELECT i.id, i.actualite_id, i.project_id, p.name, i.workspace_id, i.technologie, i.niveau, i.methode,
                       i.cree_le
                FROM actualite_impact i JOIN project p ON p.id = i.project_id
                WHERE i.actualite_id IN (
                    SELECT b.id FROM actualite a JOIN actualite b ON b.empreinte = a.empreinte WHERE a.id = ?)
                ORDER BY p.name, i.technologie
                """, Jdbc.parametres(actualiteId), lignes -> new Rapprochement(lignes.getObject(1, UUID.class),
                lignes.getObject(2, UUID.class), lignes.getObject(3, UUID.class), lignes.getString(4),
                lignes.getObject(5, UUID.class), lignes.getString(6), lignes.getString(7), lignes.getString(8),
                Conversions.instant(lignes, 9))));
    }

    /**
     * Nom de technologie d'une clé de paquet {@code pkg:type/[namespace/]nom} : la portée d'un paquet
     * npm à portée ({@code @angular/core} → {@code angular}), sinon le nom. Vide si le nom compte
     * moins de {@value #TERME_MIN} caractères.
     */
    static Optional<String> terme(String packageKey) {
        if (packageKey == null || !packageKey.startsWith("pkg:")) {
            return Optional.empty();
        }
        String reste = packageKey.substring(4);
        int slash = reste.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        String type = reste.substring(0, slash);
        String[] segments = reste.substring(slash + 1).split("/");
        String terme;
        if ("npm".equals(type) && segments.length > 1 && segments[0].startsWith("@")) {
            terme = segments[0].substring(1);
        } else {
            terme = segments[segments.length - 1];
        }
        terme = terme.strip().toLowerCase(Locale.ROOT);
        return terme.length() < TERME_MIN ? Optional.empty() : Optional.of(terme);
    }

    /** Mot entier, sans tenir compte de la casse. */
    static boolean mentionne(String contenu, String terme) {
        Pattern motif = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(terme) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return contenu != null && motif.matcher(contenu).find();
    }
}
