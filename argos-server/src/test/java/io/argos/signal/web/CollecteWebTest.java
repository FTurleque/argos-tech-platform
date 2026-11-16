package io.argos.signal.web;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.web.RecuperateurWeb;
import io.argos.ingestion.web.RecuperationRefusee;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.workspace.RegleViolee;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Collecte web : sérialisation de la collecte d'une même source, bornes d'un passage du
 * planificateur, état cohérent après l'échec de la première collecte et forme canonique des
 * adresses (constats de relecture du changement {@code collecter-veille-rss-web}).
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CollecteWebTest {

    static final String HTML = "text/html; charset=utf-8";

    @Inject
    TestDb db;

    @Inject
    CollecteWeb collecte;

    @Inject
    StockActualites stock;

    @BeforeEach
    void reset() {
        db.reset();
    }

    private static String page(String texte) {
        return "<html><head><title>Notes</title></head><body><p>" + texte + "</p></body></html>";
    }

    private static UUID ajouterPage(String user, String chemin) {
        return UUID.fromString(Api.en(user).contentType(ContentType.JSON)
                .body(Map.of("portee", "PERSONNELLE", "type", "PAGE", "adresse", StubServer.base() + chemin))
                .when().post("/api/v1/veille-web/sources").then().statusCode(201).extract().path("id"));
    }

    private void rendreEchue(UUID id) {
        db.exec("UPDATE source_web SET prochaine_collecte_le = now() - interval '1 minute' WHERE id = '" + id + "'");
    }

    // ------------------------------------------------------------------ course sur une même page

    /**
     * Une collecte à la demande et le planificateur lancés pendant une collecte en cours de la même
     * page : ni l'une ni l'autre ne la collecte en parallèle, et le changement de la page ne produit
     * qu'une actualité.
     */
    @Test
    void collecteDUneMemeSourceSerialisee() throws Exception {
        StubServer.on("/course", new Stub(200, page("Version 1.0"), Map.of("Content-Type", HTML), 0));
        UUID id = ajouterPage("alice", "/course");
        StubServer.on("/course", new Stub(200, page("Version 1.1"), Map.of("Content-Type", HTML), 600));
        rendreEchue(id);

        int requetes = StubServer.received("/course").size();
        CompletableFuture<CollecteWeb.Rapport> premiere = CompletableFuture.supplyAsync(() -> collecte.collecter(id));
        // La requête vers la page est émise sous le verrou : sa réception prouve que le verrou est pris.
        attendre(() -> StubServer.received("/course").size() > requetes);

        RegleViolee refus = assertThrows(RegleViolee.class, () -> collecte.collecter(id));
        assertEquals("COLLECTE_EN_COURS", refus.code());
        Api.en("alice").when().post("/api/v1/veille-web/sources/" + id + "/collectes").then()
                .statusCode(409).body("code", equalTo("COLLECTE_EN_COURS"));
        assertEquals(List.of(), collecte.collecterSourcesEchues(), "source en cours ignorée par le planificateur");

        CollecteWeb.Rapport rapport = premiere.get(10, TimeUnit.SECONDS);
        assertEquals("ACTIVE", rapport.etat());
        assertEquals(1, rapport.nouvelles());
        assertEquals(1, db.count("SELECT count(*) FROM actualite WHERE type = 'CHANGEMENT_PAGE'"));
        // Le verrou est libéré : la source est de nouveau collectable, sans nouveau changement.
        assertEquals(0, collecte.collecter(id).nouvelles());
    }

    /** Le planificateur ne collecte que les sources échues ; une collecte repousse l'échéance. */
    @Test
    void planificateurNeCollecteQueLesSourcesEchues() {
        StubServer.on("/echeance", new Stub(200, page("A"), Map.of("Content-Type", HTML), 0));
        UUID id = ajouterPage("alice", "/echeance");
        assertEquals(List.of(), collecte.collecterSourcesEchues());
        rendreEchue(id);
        assertEquals(1, collecte.collecterSourcesEchues().size());
        assertEquals(List.of(), collecte.collecterSourcesEchues(), "prochaine collecte repoussée d'une heure");
    }

    private static void attendre(Supplier<Boolean> condition) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < fin) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("la collecte n'a pas commencé");
    }

    // ------------------------------------------------------------------ bornes d'un passage

    @Test
    void passageBorneEnNombreEtEnDuree() {
        for (int i = 0; i < 3; i++) {
            StubServer.on("/borne" + i, new Stub(200, page("B" + i), Map.of("Content-Type", HTML), 0));
            rendreEchue(ajouterPage("alice", "/borne" + i));
        }
        CollecteWeb instance = ClientProxy.unwrap(collecte);
        int sourcesParPassage = instance.sourcesParPassage;
        Duration dureeMax = instance.dureeMaxPassage;
        Supplier<Instant> horloge = instance.horloge;
        try {
            instance.sourcesParPassage = 2;
            assertEquals(2, collecte.collecterSourcesEchues().size(), "au plus sources-par-passage sources");

            instance.sourcesParPassage = 200;
            instance.dureeMaxPassage = Duration.ofMinutes(4);
            Instant debut = Instant.now();
            long[] appels = {0};
            // Chaque lecture de l'horloge avance de 3 minutes : la deuxième source dépasserait la durée.
            instance.horloge = () -> debut.plus(Duration.ofMinutes(3 * appels[0]++));
            db.exec("UPDATE source_web SET prochaine_collecte_le = now() - interval '1 minute'");
            assertEquals(1, collecte.collecterSourcesEchues().size(), "aucune source commencée après la durée maximale");
            assertEquals(2, db.count("SELECT count(*) FROM source_web WHERE prochaine_collecte_le <= now()"),
                    "sources restantes toujours échues, reprises au passage suivant");
        } finally {
            instance.sourcesParPassage = sourcesParPassage;
            instance.dureeMaxPassage = dureeMax;
            instance.horloge = horloge;
        }
    }

    // ------------------------------------------------------------------ première collecte

    /** Une première collecte qui échoue après l'insertion laisse la source EN_ERREUR, motif générique. */
    @Test
    void premiereCollecteEnEchecLaisseLaSourceEnErreur() {
        UUID alice = Api.compte("alice");
        UUID id = UUID.randomUUID();
        db.exec("""
                INSERT INTO source_web (id, portee, proprietaire_id, type, adresse, libelle, cree_par)
                VALUES ('%1$s', 'PERSONNELLE', '%2$s', 'FLUX', 'https://exemple.test/flux', 'Flux', '%2$s')
                """.formatted(id, alice));
        RecuperateurWeb.Contenu pasUnFlux = new RecuperateurWeb.Contenu(URI.create("https://exemple.test/flux"),
                "text/html", "<html><body>pas un flux</body></html>".getBytes(StandardCharsets.UTF_8));

        CollecteWeb.Rapport rapport;
        try (StockActualites.Verrou v = stock.verrouiller(id).orElseThrow()) {
            rapport = collecte.premiereCollecte(id, pasUnFlux);
        }

        assertEquals("EN_ERREUR", rapport.etat());
        assertEquals(1, db.count("SELECT count(*) FROM source_web WHERE id = '" + id + "' AND etat = 'EN_ERREUR'"
                + " AND echecs_consecutifs = 1 AND derniere_erreur = '" + RecuperationRefusee.Motif.FLUX_INVALIDE.name()
                + " : " + RecuperationRefusee.Motif.FLUX_INVALIDE.message().replace("'", "''") + "'"
                + " AND prochaine_collecte_le > now() + interval '50 minutes'"));
        assertEquals(0, db.count("SELECT count(*) FROM actualite"));
    }

    // ------------------------------------------------------------------ forme canonique des adresses

    @Test
    void adresseCanonique() {
        assertEquals("https://exemple.test/flux", SourcesWeb.normaliser("HTTPS://Exemple.TEST:443/flux"));
        assertEquals("http://exemple.test/", SourcesWeb.normaliser("http://EXEMPLE.test:80"));
        assertEquals("http://exemple.test:8080/a/b?x=1", SourcesWeb.normaliser("http://exemple.test:8080/a/./b?x=1#frag"));
        assertEquals("https://exemple.test/Chemin/?utm_source=x", SourcesWeb.normaliser("https://exemple.test/Chemin/?utm_source=x"),
                "chemin et paramètres conservés tels quels");
        assertEquals(RecuperationRefusee.Motif.ADRESSE_INVALIDE, assertThrows(RecuperationRefusee.class,
                () -> SourcesWeb.normaliser("http://user:secret@exemple.test/")).motif());
        assertEquals(RecuperationRefusee.Motif.ADRESSE_INVALIDE, assertThrows(RecuperationRefusee.class,
                () -> SourcesWeb.normaliser("ftp://exemple.test/")).motif());
    }

    /** Une même adresse écrite autrement (casse, fragment) ne contourne pas l'unicité. */
    @Test
    void uniciteSurLAdresseCanonique() {
        StubServer.on("/unique.xml", new Stub(200, """
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom"><title>U</title><id>urn:u</id><updated>2026-11-15T10:00:00Z</updated></feed>
                """, Map.of("Content-Type", "application/atom+xml"), 0));
        String base = StubServer.base();
        String variante = base.replace("http://localhost", "HTTP://LOCALHOST") + "/unique.xml#haut";
        Api.en("alice").contentType(ContentType.JSON)
                .body(Map.of("portee", "PERSONNELLE", "type", "FLUX", "adresse", base + "/unique.xml"))
                .when().post("/api/v1/veille-web/sources").then().statusCode(201)
                .body("adresse", equalTo(base + "/unique.xml"));
        Api.en("alice").contentType(ContentType.JSON)
                .body(Map.of("portee", "PERSONNELLE", "type", "FLUX", "adresse", variante))
                .when().post("/api/v1/veille-web/sources").then().statusCode(409).body("code", equalTo("SOURCE_EXISTANTE"));
        assertTrue(db.count("SELECT count(*) FROM source_web") == 1);
    }
}
