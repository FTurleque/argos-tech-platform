package io.argos.workspace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.horloge.LectureHeure;
import io.argos.workspace.ContexteBase.Contexte;

/**
 * Comptes Argos (ACC-02).
 *
 * <p>Le compte est créé à la première connexion OIDC réussie, sans aucune appartenance
 * (design.md D2). En mode {@code invitation} (Q-2), la création exige une invitation en
 * attente adressée à l'e-mail vérifié de la personne, ou l'adresse de l'administrateur
 * plateforme initial.
 */
@ApplicationScoped
public class ComptesService {

    /** Mode d'inscription : {@code invitation} (décision Q-2) ou {@code ouverte}. */
    @ConfigProperty(name = "argos.comptes.inscription", defaultValue = "invitation")
    String modeInscription;

    /** E-mail vérifié de l'administrateur plateforme créé à sa première connexion. */
    @ConfigProperty(name = "argos.comptes.admin-plateforme-initial")
    Optional<String> adminPlateformeInitial;

    /** Profil de veille activé à la création d'un compte (Q-3). */
    @ConfigProperty(name = "argos.comptes.profil-par-defaut", defaultValue = "developpeur")
    String profilParDefaut;

    @Inject
    Clock horloge;

    @Inject
    ContexteBase base;

    @Inject
    Event<CompteCree> compteCree;

    /** Retourne le compte de la personne, en le créant à sa première connexion. */
    public Compte obtenirOuCreer(IdentiteOidc identite) {
        if (identite == null || identite.sujet() == null || identite.sujet().isBlank()) {
            throw new DonneeInvalide("Jeton sans sujet.");
        }
        String email = identite.emailVerifieNormalise();
        Contexte contexte = new Contexte(null, identite.sujet(), email, null);
        Optional<Compte> existant = base.executer(contexte, connexion -> parSujet(connexion, identite.sujet()));
        if (existant.isPresent()) {
            return Objects.equals(existant.get().email(), email) ? existant.get()
                    : base.executer(contexte, connexion -> actualiserAdresse(connexion, identite.sujet(), email));
        }
        boolean adminInitial = adminInitial(email);
        Creation creation = base.executer(contexte, connexion -> creer(connexion, identite, adminInitial));
        if (creation.nouveau()) {
            compteCree.fire(new CompteCree(creation.compte(), profilParDefaut));
        }
        return creation.compte();
    }

    /** Vrai si l'e-mail vérifié est celui de l'administrateur plateforme initial. */
    private boolean adminInitial(String email) {
        return email != null && adminPlateformeInitial.map(adresse -> adresse.trim().toLowerCase(Locale.ROOT))
                .filter(email::equals).isPresent();
    }

    /**
     * Crée le compte du sujet si l'inscription est admise (administrateur initial, inscription ouverte ou invitation en
     * attente) ; une création concurrente du même sujet n'en produit pas de second.
     */
    private Creation creer(Connection connexion, IdentiteOidc identite, boolean adminInitial) throws SQLException {
        String adresseVerifiee = identite.emailVerifieNormalise();
        boolean admise = adminInitial || "ouverte".equalsIgnoreCase(modeInscription)
                || invitationEnAttente(connexion, LectureHeure.dateHeure(horloge));
        if (!admise) {
            throw new InscriptionRefusee();
        }
        UUID id = UUID.randomUUID();
        int insere;
        try (PreparedStatement requete = connexion.prepareStatement(
                "INSERT INTO compte (id, sujet_oidc, nom_affiche, email, admin_plateforme) VALUES (?, ?, ?, ?, ?)"
                        + " ON CONFLICT (sujet_oidc) DO NOTHING")) {
            requete.setObject(1, id);
            requete.setString(2, identite.sujet());
            requete.setString(3, nomAffiche(identite));
            requete.setString(4, adresseVerifiee);
            requete.setBoolean(5, adminInitial);
            insere = requete.executeUpdate();
        }
        return new Creation(parSujet(connexion, identite.sujet()).orElseThrow(), insere == 1);
    }

    /**
     * Aligne l'adresse enregistrée sur celle que le fournisseur déclare vérifiée dans le dernier jeton (ALR-08) :
     * l'adresse vérifiée normalisée, ou aucune. Écriture conditionnelle : aucune écriture tant que l'adresse ne change pas.
     */
    private static Compte actualiserAdresse(Connection connexion, String sujet, String adresseVerifiee) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "UPDATE compte SET email = ? WHERE sujet_oidc = ? AND email IS DISTINCT FROM ?")) {
            requete.setString(1, adresseVerifiee);
            requete.setString(2, sujet);
            requete.setString(3, adresseVerifiee);
            requete.executeUpdate();
        }
        return parSujet(connexion, sujet).orElseThrow();
    }

    /** Compte associé à un sujet OIDC, s'il existe. */
    public Optional<Compte> parSujet(String sujet) {
        return base.executer(new Contexte(null, sujet, null, null), connexion -> parSujet(connexion, sujet));
    }

    private record Creation(Compte compte, boolean nouveau) {
    }

    /** Vrai si une invitation en attente, non expirée à {@code maintenant}, est adressée à l'e-mail du contexte. */
    private static boolean invitationEnAttente(Connection connexion, OffsetDateTime maintenant) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM invitation WHERE etat = 'EN_ATTENTE' AND expire_le > ?"
                        + " AND lower(email) = argos_email_courant())")) {
            requete.setObject(1, maintenant);
            try (ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getBoolean(1);
            }
        }
    }

    static Optional<Compte> parSujet(Connection connexion, String sujet) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT id, sujet_oidc, nom_affiche, email, admin_plateforme, cree_le FROM compte WHERE sujet_oidc = ?")) {
            requete.setString(1, sujet);
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Compte(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                        resultat.getString(4), resultat.getBoolean(5), resultat.getObject(6, OffsetDateTime.class)));
            }
        }
    }

    private static String nomAffiche(IdentiteOidc identite) {
        if (identite.nom() != null && !identite.nom().isBlank()) {
            return identite.nom().trim();
        }
        return identite.email() != null ? identite.email() : identite.sujet();
    }
}
