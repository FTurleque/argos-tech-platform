package io.argos.workspace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

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
        Optional<Compte> existant = base.executer(contexte, c -> parSujet(c, identite.sujet()));
        if (existant.isPresent()) {
            return existant.get();
        }
        boolean adminInitial = email != null && adminPlateformeInitial
                .map(a -> a.trim().toLowerCase(Locale.ROOT)).filter(email::equals).isPresent();
        Creation creation = base.executer(contexte, c -> {
            if (!adminInitial && !"ouverte".equalsIgnoreCase(modeInscription) && !invitationEnAttente(c)) {
                throw new InscriptionRefusee();
            }
            UUID id = UUID.randomUUID();
            int insere;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO compte (id, sujet_oidc, nom_affiche, email, admin_plateforme) VALUES (?, ?, ?, ?, ?)"
                            + " ON CONFLICT (sujet_oidc) DO NOTHING")) {
                ps.setObject(1, id);
                ps.setString(2, identite.sujet());
                ps.setString(3, nomAffiche(identite));
                ps.setString(4, identite.email());
                ps.setBoolean(5, adminInitial);
                insere = ps.executeUpdate();
            }
            return new Creation(parSujet(c, identite.sujet()).orElseThrow(), insere == 1);
        });
        if (creation.nouveau()) {
            compteCree.fire(new CompteCree(creation.compte(), profilParDefaut));
        }
        return creation.compte();
    }

    /** Compte associé à un sujet OIDC, s'il existe. */
    public Optional<Compte> parSujet(String sujet) {
        return base.executer(new Contexte(null, sujet, null, null), c -> parSujet(c, sujet));
    }

    private record Creation(Compte compte, boolean nouveau) {
    }

    private static boolean invitationEnAttente(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM invitation WHERE etat = 'EN_ATTENTE' AND expire_le > now()"
                        + " AND lower(email) = argos_email_courant())");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    static Optional<Compte> parSujet(Connection c, String sujet) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, sujet_oidc, nom_affiche, email, admin_plateforme, cree_le FROM compte WHERE sujet_oidc = ?")) {
            ps.setString(1, sujet);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Compte(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5), rs.getObject(6, OffsetDateTime.class)));
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
