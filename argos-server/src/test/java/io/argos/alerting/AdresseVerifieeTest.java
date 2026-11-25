package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.argos.workspace.Compte;
import io.argos.workspace.ComptesService;
import io.argos.workspace.IdentiteOidc;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;

/**
 * ALR-08 : un e-mail ne part que vers l'adresse que le fournisseur d'identité déclare vérifiée, tenue à jour à chaque
 * connexion. Le compte d'alice est connu du fournisseur de test ; les jetons sont simulés par des identités OIDC
 * (adresse vérifiée ou non). La boîte aux lettres de Quarkus Mailer est simulée.
 */
@QuarkusTest
class AdresseVerifieeTest {

    private static final String ADRESSE_VERIFIEE = "alice@argos.test";
    private static final String ADRESSE_NON_VERIFIEE = "pirate@exemple.test";
    private static final String NOUVELLE_ADRESSE = "nouvelle@argos.test";
    private static final String ANCIENNE_ADRESSE = "ancienne@argos.test";
    private static final String MIGRATION = "src/main/resources/db/migration/V23__adresse_verifiee_des_comptes.sql";
    private static final Pattern EFFACEMENT = Pattern.compile("^UPDATE compte SET email = NULL;$", Pattern.MULTILINE);

    @Inject
    TestDb baseDeTest;

    @Inject
    ComptesService comptes;

    @Inject
    EnvoiAlertes envoi;

    @Inject
    MockMailbox mailbox;

    private UUID compte;
    private String sujet;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        mailbox.clear();
        compte = Api.compte("alice");
        sujet = baseDeTest.string("SELECT sujet_oidc FROM compte WHERE id = ?", compte);
    }

    private Compte seConnecter(String adresse, boolean verifiee) {
        return comptes.obtenirOuCreer(new IdentiteOidc(sujet, "Alice", adresse, verifiee));
    }

    /** Alerte d'un résumé quotidien au canal e-mail pour le compte d'alice, puis tentative d'envoi. */
    private EnvoiAlertes.Issue diffuserUnResume() {
        UUID id = UUID.randomUUID();
        String contenu = "{\"elements\":[{\"signalId\":\"" + UUID.randomUUID() + "\",\"titre\":\"Faille\","
                + "\"gravite\":\"HIGH\",\"projets\":[],\"sources\":[],\"lien\":null}],\"resumeIa\":null,"
                + "\"etatResumeIa\":\"SANS_OBJET\"}";
        baseDeTest.exec("INSERT INTO alerte(id, nature, compte_id, canal, fin_periode, titre, contenu, etat_resume_ia, etat, cree_le) "
                + "VALUES (?, 'DAILY', ?, 'EMAIL', now(), 'Résumé', ?::jsonb, 'SANS_OBJET', 'EN_ATTENTE', now())",
                id, compte, contenu);
        EnvoiAlertes.Issue issue = envoi.envoyer(List.of(id), Instant.now()).get(id);
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM alerte WHERE id = ?", id),
                "l'élément reste visible dans Argos");
        return issue;
    }

    /** ALR-08-S1 : jeton à adresse non vérifiée, aucun e-mail, ni vers cette adresse ni vers une autre. */
    @Test
    void alr08S1AdresseNonVerifieeSansEmail() {
        Compte connecte = seConnecter(ADRESSE_NON_VERIFIEE, false);

        assertNull(connecte.email());
        assertNull(baseDeTest.string("SELECT email FROM compte WHERE id = ?", compte));
        assertEquals(EnvoiAlertes.Issue.EN_ECHEC, diffuserUnResume());
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    /** ALR-08-S2 : adresse vérifiée, enregistrée normalisée, e-mail envoyé à cette adresse. */
    @Test
    void alr08S2AdresseVerifieeEnvoi() {
        Compte connecte = seConnecter("Alice@Argos.TEST", true);

        assertEquals(ADRESSE_VERIFIEE, connecte.email());
        assertEquals(EnvoiAlertes.Issue.ENVOYEE, diffuserUnResume());
        assertEquals(1, mailbox.getMessagesSentTo(ADRESSE_VERIFIEE).size());
        assertEquals(1, mailbox.getTotalMessagesSent());
    }

    /** ALR-08-S3 : adresse vérifiée modifiée chez le fournisseur, reconnexion, e-mail à la nouvelle adresse seulement. */
    @Test
    void alr08S3AdresseModifieeApresReconnexion() {
        seConnecter(ADRESSE_VERIFIEE, true);
        assertEquals(EnvoiAlertes.Issue.ENVOYEE, diffuserUnResume());
        mailbox.clear();

        Compte reconnecte = seConnecter(NOUVELLE_ADRESSE, true);

        assertEquals(NOUVELLE_ADRESSE, reconnecte.email());
        assertEquals(EnvoiAlertes.Issue.ENVOYEE, diffuserUnResume());
        assertEquals(1, mailbox.getMessagesSentTo(NOUVELLE_ADRESSE).size());
        assertTrue(mailbox.getMessagesSentTo(ADRESSE_VERIFIEE).isEmpty(), "plus aucun e-mail vers l'ancienne adresse");
    }

    /** ALR-08-S3 : une adresse devenue non vérifiée est oubliée à la connexion suivante. */
    @Test
    void alr08S3AdresseDevenueNonVerifieeOubliee() {
        seConnecter(ADRESSE_VERIFIEE, true);

        assertNull(seConnecter(ADRESSE_VERIFIEE, false).email());
        assertEquals(EnvoiAlertes.Issue.EN_ECHEC, diffuserUnResume());
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    /**
     * ALR-08-S4 : après la migration qui efface les adresses enregistrées, aucun e-mail avant la reconnexion ; ensuite
     * les e-mails partent à l'adresse vérifiée du jeton.
     */
    @Test
    void alr08S4CompteExistantAvantLaMigration() throws IOException {
        baseDeTest.exec("UPDATE compte SET email = ? WHERE id = ?", ANCIENNE_ADRESSE, compte);
        Matcher effacement = EFFACEMENT.matcher(Files.readString(Path.of(MIGRATION)));
        assertTrue(effacement.find(), "la migration efface les adresses enregistrées");
        baseDeTest.exec(effacement.group());

        assertNull(baseDeTest.string("SELECT email FROM compte WHERE id = ?", compte));
        assertEquals(EnvoiAlertes.Issue.EN_ECHEC, diffuserUnResume());
        assertEquals(0, mailbox.getTotalMessagesSent(), "aucun e-mail avant la reconnexion");

        seConnecter(ADRESSE_VERIFIEE, true);

        assertEquals(EnvoiAlertes.Issue.ENVOYEE, diffuserUnResume());
        assertEquals(1, mailbox.getMessagesSentTo(ADRESSE_VERIFIEE).size());
    }
}
