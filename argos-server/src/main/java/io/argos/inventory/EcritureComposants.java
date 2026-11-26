package io.argos.inventory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Écriture par lot des composants et des occurrences d'un emplacement (INV-14, AUD-PER-20) : trois instructions,
 * quel que soit le nombre de composants, les valeurs étant passées en tableaux ({@code unnest}). Un composant déjà
 * présent garde son identifiant et n'est réécrit que si son PURL change.
 *
 * <p>Les composants lus sont d'abord dédoublonnés par paquet et version : une instruction {@code ON CONFLICT} ne peut
 * pas toucher deux fois la même ligne. Un composant présent en direct et en transitif est direct ; transitif s'il est
 * transitif au moins une fois et jamais direct ; inconnu sinon.
 */
final class EcritureComposants {

    private static final String INSERTION_COMPOSANTS = """
            INSERT INTO component(id, project_id, package_key, purl, version, transitive, import_id)
            SELECT u.id, ?, u.cle, u.purl, u.version, u.transitive, ?
            FROM unnest(?::uuid[], ?::text[], ?::text[], ?::text[], ?::boolean[]) AS u(id, cle, purl, version, transitive)
            ON CONFLICT (project_id, package_key, (coalesce(version, ''))) DO NOTHING
            """;

    private static final String MISE_A_JOUR_PURL = """
            UPDATE component c SET purl = u.purl, import_id = ?
            FROM unnest(?::text[], ?::text[], ?::text[]) AS u(cle, purl, version)
            WHERE c.project_id = ? AND c.package_key = u.cle AND coalesce(c.version, '') = coalesce(u.version, '')
              AND c.purl IS DISTINCT FROM u.purl
            """;

    private static final String INSERTION_OCCURRENCES = """
            INSERT INTO component_occurrence(component_id, repository_id, project_id, transitive, import_id)
            SELECT c.id, ?, ?, u.transitive, ?
            FROM unnest(?::text[], ?::text[], ?::boolean[]) AS u(cle, version, transitive)
            JOIN component c ON c.project_id = ? AND c.package_key = u.cle AND coalesce(c.version, '') = coalesce(u.version, '')
            ON CONFLICT (component_id, (coalesce(repository_id, '00000000-0000-0000-0000-000000000000'::uuid)))
            DO UPDATE SET transitive = CASE
                WHEN component_occurrence.transitive = false OR excluded.transitive = false THEN false
                ELSE coalesce(component_occurrence.transitive, excluded.transitive) END
            """;

    private static final char SEPARATEUR_CLE = '\u0000';

    private EcritureComposants() {
    }

    /**
     * Écrit les composants de l'emplacement et leurs occurrences ; les occurrences précédentes de l'emplacement sont
     * déjà supprimées par l'appelant.
     */
    static void ecrire(Connection connexion, UUID projectId, UUID repositoryId, List<ParsedInventory.Item> lus, UUID importId)
            throws SQLException {
        List<ParsedInventory.Item> composants = dedoublonner(lus);
        if (composants.isEmpty()) {
            return;
        }
        Tableaux tableaux = Tableaux.de(connexion, composants);
        try (PreparedStatement requete = connexion.prepareStatement(INSERTION_COMPOSANTS)) {
            requete.setObject(1, projectId);
            requete.setObject(2, importId);
            requete.setArray(3, tableaux.identifiants());
            requete.setArray(4, tableaux.cles());
            requete.setArray(5, tableaux.purls());
            requete.setArray(6, tableaux.versions());
            requete.setArray(7, tableaux.transitifs());
            requete.executeUpdate();
        }
        try (PreparedStatement requete = connexion.prepareStatement(MISE_A_JOUR_PURL)) {
            requete.setObject(1, importId);
            requete.setArray(2, tableaux.cles());
            requete.setArray(3, tableaux.purls());
            requete.setArray(4, tableaux.versions());
            requete.setObject(5, projectId);
            requete.executeUpdate();
        }
        try (PreparedStatement requete = connexion.prepareStatement(INSERTION_OCCURRENCES)) {
            requete.setObject(1, repositoryId);
            requete.setObject(2, projectId);
            requete.setObject(3, importId);
            requete.setArray(4, tableaux.cles());
            requete.setArray(5, tableaux.versions());
            requete.setArray(6, tableaux.transitifs());
            requete.setObject(7, projectId);
            requete.executeUpdate();
        }
    }

    /** Un composant par paquet et version (version absente et vide confondues), dans l'ordre de première lecture. */
    static List<ParsedInventory.Item> dedoublonner(List<ParsedInventory.Item> lus) {
        Map<String, ParsedInventory.Item> uniques = new LinkedHashMap<>();
        for (ParsedInventory.Item lu : lus) {
            String cle = lu.packageKey() + SEPARATEUR_CLE + (lu.version() == null ? "" : lu.version());
            uniques.merge(cle, lu, (premier, doublon) -> new ParsedInventory.Item(premier.packageKey(), premier.purl(),
                    premier.version(), fusionnerTransitif(premier.transitive(), doublon.transitive())));
        }
        return new ArrayList<>(uniques.values());
    }

    /** Direct dès qu'une occurrence est directe ; transitif si une occurrence l'est ; inconnu sinon. */
    static Boolean fusionnerTransitif(Boolean premier, Boolean second) {
        if (Boolean.FALSE.equals(premier) || Boolean.FALSE.equals(second)) {
            return Boolean.FALSE;
        }
        if (Boolean.TRUE.equals(premier) || Boolean.TRUE.equals(second)) {
            return Boolean.TRUE;
        }
        return null;
    }

    /** Colonnes des composants à écrire, en tableaux SQL de même longueur. */
    private record Tableaux(Array identifiants, Array cles, Array purls, Array versions, Array transitifs) {

        static Tableaux de(Connection connexion, List<ParsedInventory.Item> composants) throws SQLException {
            int nombre = composants.size();
            UUID[] identifiants = new UUID[nombre];
            String[] cles = new String[nombre];
            String[] purls = new String[nombre];
            String[] versions = new String[nombre];
            Boolean[] transitifs = new Boolean[nombre];
            for (int rang = 0; rang < nombre; rang++) {
                ParsedInventory.Item composant = composants.get(rang);
                identifiants[rang] = UUID.randomUUID();
                cles[rang] = composant.packageKey();
                purls[rang] = composant.purl();
                versions[rang] = composant.version();
                transitifs[rang] = composant.transitive();
            }
            return new Tableaux(connexion.createArrayOf("uuid", identifiants), connexion.createArrayOf("text", cles),
                    connexion.createArrayOf("text", purls), connexion.createArrayOf("text", versions),
                    connexion.createArrayOf("bool", transitifs));
        }
    }
}
