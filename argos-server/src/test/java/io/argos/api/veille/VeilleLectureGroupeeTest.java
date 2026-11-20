package io.argos.api.veille;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.signal.SignalQueries;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Contenu de « Ma veille » ({@code GET /api/v1/me/veille}) inchangé par la lecture groupée des signaux et par
 * l'index des impacts récents (changement {@code optimiser-ma-veille}, PRF-07-S2).
 *
 * <p>Le jeu de données, écrit hors RLS comme la collecte et la corrélation, réunit plusieurs avis aux dates
 * distinctes (dont un sans date de publication), plusieurs projets par avis, un impact écarté, un avis inconnu
 * de l'espace et un projet d'un espace dont l'appelant n'est pas membre. La réponse de l'API est comparée à un
 * résultat attendu calculé indépendamment, en base, et à la lecture unitaire {@link SignalQueries#find(UUID)}.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class VeilleLectureGroupeeTest {

    private static final int AVIS = 6;

    @Inject
    TestDb baseDeTest;

    @Inject
    DataSource dataSource;

    @Inject
    SignalQueries signaux;

    private UUID espace;
    private UUID autreEspace;
    private final List<UUID> signauxIds = new ArrayList<>();

    private static UUID id(String graine) {
        return UUID.nameUUIDFromBytes(graine.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        supprimerPreferences(Api.compte("jules"));
        espace = Api.nouvelEspace("ines", "Lecture groupée");
        Api.ajouterMembre("ines", espace.toString(), "jules", "LECTEUR");
        autreEspace = Api.nouvelEspace("oscar", "Lecture groupée, autre espace");
        Api.en("jules").contentType(ContentType.JSON).body(Map.of("profilsActifs", List.of("developpeur")))
                .when().put("/api/v1/preferences/profils").then().statusCode(200);

        signauxIds.clear();
        projet("p1", espace);
        projet("p2", espace);
        projet("p3", espace);
        projet("etranger", autreEspace);
        for (int numero = 1; numero <= AVIS + 1; numero++) {
            UUID signalId = id("lg-signal-" + numero);
            UUID versionId = id("lg-version-" + numero);
            // L'avis 3 n'a pas de date de publication : sa date restituée est celle de modification.
            String publie = numero == 3 ? "NULL" : "now() - interval '" + (numero * 3) + " days'";
            // Contrainte signal_current_version_fk différée : le signal et sa version dans une même transaction.
            baseDeTest.exec("""
                    BEGIN;
                    INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                                        collected_at, updated_at, content_hash, affected, current_version_id)
                    VALUES ('%1$s', 'OSV', 'GHSA-LG-%3$d', 'Avis lecture groupée %3$d', 'Résumé', '%4$s', %5$s,
                            now() - interval '%6$d hours', now(), now(), md5('%3$d') || md5('lg%3$d'), '[]', '%2$s');
                    INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at,
                                                raw_content)
                    VALUES ('%2$s', '%1$s', 1, md5('%3$d') || md5('lg%3$d'), now(), now(), '{}');
                    COMMIT;
                    """.formatted(signalId, versionId, numero, numero % 2 == 0 ? "CRITICAL" : "HIGH", publie,
                    numero * 5));
            baseDeTest.exec("INSERT INTO signal_alias (signal_id, alias) VALUES ('%s', 'CVE-2099-%04d')".formatted(signalId,
                    numero));
            signauxIds.add(signalId);
        }
        // Avis 1 à AVIS : un impact par projet de l'espace, à des instants distincts ; l'avis AVIS + 1 n'a aucun
        // impact dans l'espace de l'appelant (projet d'un autre espace seulement).
        int rang = 0;
        for (int numero = 1; numero <= AVIS; numero++) {
            for (String projet : List.of("p1", "p2", "p3")) {
                impact(numero, projet, espace, "OUVERT", rang++);
            }
            impact(numero, "etranger", autreEspace, "OUVERT", rang++);
        }
        impact(AVIS + 1, "etranger", autreEspace, "CONFIRME", rang++);
        // Impact écarté : jamais restitué (COR-12-S2), même s'il est le plus récent.
        baseDeTest.exec("UPDATE project_impact SET state = 'ECARTE', updated_at = now() + interval '1 hour' WHERE id = '"
                + id("lg-impact-2-p3") + "'");
    }

    /** Retire les préférences du compte, par une requête paramétrée. */
    private void supprimerPreferences(UUID compte) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion
                .prepareStatement("DELETE FROM workspace_preference_utilisateur WHERE compte_id = ?")) {
            requete.setObject(1, compte);
            requete.executeUpdate();
        } catch (SQLException erreur) {
            throw new IllegalStateException("suppression des préférences impossible", erreur);
        }
    }

    private void projet(String nom, UUID espaceProjet) {
        UUID projetId = id("lg-projet-" + nom);
        UUID importId = id("lg-import-" + nom);
        baseDeTest.exec("INSERT INTO project (id, workspace_id, name) VALUES ('%s', '%s', 'Projet %s')".formatted(projetId,
                espaceProjet, nom));
        baseDeTest.exec("""
                INSERT INTO inventory (project_id, origin, source_date, spec_version, source_ref, import_id, imported_at)
                VALUES ('%s', 'SBOM', now() - interval '1 day', '1.6', 'test', '%s', now())
                """.formatted(projetId, importId));
        for (int numero = 1; numero <= AVIS + 1; numero++) {
            baseDeTest.exec("""
                    INSERT INTO component (id, project_id, package_key, purl, version, transitive, import_id)
                    VALUES ('%s', '%s', 'pkg:maven/org.lg/lib-%d', 'pkg:maven/org.lg/lib-%d@1.0.0', '1.0.0', false, '%s')
                    """.formatted(id("lg-composant-" + nom + "-" + numero), projetId, numero, numero, importId));
        }
    }

    private void impact(int numero, String projet, UUID espaceProjet, String etat, int rang) {
        baseDeTest.exec("""
                INSERT INTO project_impact (id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                                            level, method, transitive, inventory_origin, inventory_date, provenance,
                                            created_at, updated_at, state)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'EXACT', 'PURL_RANGE_V1', false, 'SBOM',
                        now() - interval '1 day', '{}', now(), now() - interval '%d minutes', '%s')
                """.formatted(id("lg-impact-" + numero + "-" + projet), id("lg-signal-" + numero),
                id("lg-version-" + numero), id("lg-projet-" + projet), espaceProjet,
                id("lg-composant-" + projet + "-" + numero), rang, etat));
    }

    /** Résultat attendu, calculé en base hors de l'application : avis, gravité, date et projets visibles. */
    private record Attendu(String identifiant, String gravite, OffsetDateTime date, Set<String> projets) {
    }

    private List<Attendu> attendus() throws SQLException {
        Map<String, Attendu> parAvis = new LinkedHashMap<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT s.external_id, s.severity, coalesce(s.published_at, s.modified_at), p.id
                FROM project_impact i JOIN signal s ON s.id = i.signal_id JOIN project p ON p.id = i.project_id
                WHERE i.workspace_id = ? AND i.state IN ('OUVERT', 'CONFIRME')
                ORDER BY coalesce(s.published_at, s.modified_at) DESC, i.created_at, i.id
                """)) {
            requete.setObject(1, espace);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    ajouterLigne(parAvis, resultat);
                }
            }
        }
        return List.copyOf(parAvis.values());
    }

    /** Range une ligne du calcul de référence : l'avis à sa première rencontre, puis son projet. */
    private static void ajouterLigne(Map<String, Attendu> parAvis, ResultSet resultat) throws SQLException {
        String identifiant = resultat.getString(1);
        Attendu attendu = parAvis.get(identifiant);
        if (attendu == null) {
            attendu = new Attendu(identifiant, resultat.getString(2), resultat.getObject(3, OffsetDateTime.class),
                    new LinkedHashSet<>());
            parAvis.put(identifiant, attendu);
        }
        attendu.projets().add(resultat.getObject(4, UUID.class).toString());
    }

    /**
     * PRF-07-S2 : mêmes éléments, mêmes champs, même ordre que le calcul de référence ; aucun projet ni impact
     * écarté hors du périmètre de l'appelant ; gravité et date identiques à la lecture unitaire d'un signal.
     */
    @Test
    void prf07ContenuIdentiqueAuCalculDeReference() throws SQLException {
        JsonPath veille = Api.en("jules").when().get("/api/v1/me/veille").then().statusCode(200).extract().jsonPath();
        List<Attendu> attendus = attendus();
        assertEquals(AVIS, attendus.size(), "jeu de données : un avis par signal impactant l'espace");

        assertEquals(attendus.stream().map(Attendu::identifiant).toList(), veille.getList("elements.identifiant"),
                "mêmes avis, du plus récent au plus ancien ; l'avis sans impact dans l'espace est absent");
        for (int position = 0; position < attendus.size(); position++) {
            Attendu attendu = attendus.get(position);
            String element = "elements[" + position + "].";
            assertEquals("AVIS_SECURITE", veille.getString(element + "type"));
            assertEquals("OSV", veille.getString(element + "source"));
            assertEquals(attendu.gravite(), veille.getString(element + "gravite"), attendu.identifiant());
            assertEquals(attendu.date().toInstant(), java.time.Instant.parse(veille.getString(element + "date")),
                    attendu.identifiant());
            assertEquals(attendu.projets(),
                    new LinkedHashSet<>(veille.getList(element + "projets.projetId", String.class)),
                    attendu.identifiant() + " : seuls les projets visibles de l'espace de l'appelant");
            assertEquals(List.of("developpeur"), veille.getList(element + "profils"));
            assertEquals(List.of("securite"), veille.getList(element + "rubriques.identifiant"));
            assertTrue(veille.getList(element + "abonnements").isEmpty());

            // Lecture unitaire de référence : même gravité, même date que la lecture groupée.
            UUID signalId = UUID.fromString(veille.getString(element + "id"));
            SignalQueries.SignalView unitaire = signaux.find(signalId).orElseThrow();
            assertEquals(unitaire.severity(), veille.getString(element + "gravite"));
            assertEquals(unitaire.publishedAt() != null ? unitaire.publishedAt() : unitaire.modifiedAt(),
                    java.time.Instant.parse(veille.getString(element + "date")));
        }
        assertFalse(veille.prettify().contains(id("lg-projet-etranger").toString()), "aucun projet d'un autre espace");
        assertEquals(List.of("p1", "p2"),
                veille.getList("elements.find { it.identifiant == 'GHSA-LG-2' }.projets.projet", String.class).stream()
                        .map(nom -> nom.substring("Projet ".length())).sorted().toList(),
                "l'impact écarté de l'avis 2 sur p3 n'est pas restitué");
    }

    /** Lecture groupée : chaque vue est celle de la lecture unitaire ; ordre des identifiants, inconnus absents. */
    @Test
    void lectureGroupeeEgaleALaLectureUnitaire() {
        List<UUID> demandes = new ArrayList<>(signauxIds);
        java.util.Collections.reverse(demandes);
        UUID inconnu = id("lg-signal-inconnu");
        demandes.add(1, inconnu);
        demandes.add(demandes.get(0));

        Map<UUID, SignalQueries.SignalView> lus = signaux.findAll(demandes);
        List<UUID> attendus = new ArrayList<>(new LinkedHashSet<>(demandes));
        attendus.remove(inconnu);
        assertEquals(attendus, List.copyOf(lus.keySet()), "ordre des identifiants donnés, sans doublon ni inconnu");
        for (UUID signalId : signauxIds) {
            assertEquals(signaux.find(signalId).orElseThrow(), lus.get(signalId));
        }
        assertTrue(signaux.findAll(List.of()).isEmpty());
    }
}
