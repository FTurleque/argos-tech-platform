package io.argos.workspace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.workspace.ContexteBase.Contexte;

/**
 * Espaces, appartenances, rôles et invitations (ACC-03, ACC-04, ACC-05).
 *
 * <p>Chaque opération vérifie d'abord l'appartenance et le rôle du compte ({@link #exiger}),
 * puis s'exécute avec l'espace courant transmis à PostgreSQL, dont la RLS refait le contrôle.
 * L'appartenance est relue à chaque requête : une révocation s'applique dès la requête suivante.
 */
@ApplicationScoped
public class EspacesService {

    /** Espace vu par un de ses membres. */
    public record EspaceDeCompte(UUID id, String nom, Role role, OffsetDateTime creeLe) {
    }

    /** Membre d'un espace. */
    public record Membre(UUID compteId, String nomAffiche, Role role, OffsetDateTime depuis) {
    }

    /** Invitation à rejoindre un espace. */
    public record Invitation(UUID id, UUID espaceId, String email, Role role, String etat, String invitePar,
            OffsetDateTime creeLe, OffsetDateTime expireLe) {
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @ConfigProperty(name = "argos.invitations.validite-jours", defaultValue = "14")
    int validiteJours;

    @Inject
    ContexteBase base;

    // ------------------------------------------------------------------ contrôle

    /**
     * Vérifie que le compte est membre de l'espace avec au moins le rôle demandé.
     *
     * @throws AccesRefuse {@link MotifRefus#NON_MEMBRE} si le compte n'est pas membre ou si l'espace
     *                     n'existe pas, {@link MotifRefus#ROLE_INSUFFISANT} si le rôle ne suffit pas
     */
    public Role exiger(Compte compte, UUID espaceId, Role minimum) {
        Optional<Role> role = base.executer(new Contexte(compte.id(), null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT role FROM appartenance WHERE espace_id = ? AND compte_id = argos_compte_courant()")) {
                requete.setObject(1, espaceId);
                try (ResultSet resultat = requete.executeQuery()) {
                    return resultat.next() ? Optional.of(Role.valueOf(resultat.getString(1))) : Optional.<Role>empty();
                }
            }
        });
        if (role.isEmpty()) {
            throw new AccesRefuse(MotifRefus.NON_MEMBRE);
        }
        if (!role.get().auMoins(minimum)) {
            throw new AccesRefuse(MotifRefus.ROLE_INSUFFISANT);
        }
        return role.get();
    }

    // ------------------------------------------------------------------ espaces

    /** Crée un espace dont le compte devient administrateur (ACC-03-S1). */
    public EspaceDeCompte creer(Compte compte, String nom) {
        String nomPropre = nom == null ? "" : nom.trim();
        if (nomPropre.isEmpty() || nomPropre.length() > 120) {
            throw new DonneeInvalide("Le nom de l'espace doit contenir de 1 à 120 caractères.");
        }
        UUID id = UUID.randomUUID();
        return base.executer(new Contexte(compte.id(), null, id), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT INTO espace (id, nom, cree_par) VALUES (?, ?, argos_compte_courant())")) {
                requete.setObject(1, id);
                requete.setString(2, nomPropre);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT INTO appartenance (espace_id, compte_id, role) VALUES (?, argos_compte_courant(), 'ADMINISTRATEUR')")) {
                requete.setObject(1, id);
                requete.executeUpdate();
            }
            return lireEspace(connexion, id).orElseThrow();
        });
    }

    /** Espaces dont le compte est membre ; vide pour un nouveau compte (ACC-02-S1). */
    public List<EspaceDeCompte> mesEspaces(Compte compte) {
        return base.executer(new Contexte(compte.id(), null, null), connexion -> {
            List<EspaceDeCompte> espaces = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT e.id, e.nom, a.role, e.cree_le FROM appartenance a JOIN espace e ON e.id = a.espace_id"
                            + " WHERE a.compte_id = argos_compte_courant() ORDER BY e.nom, e.id");
                    ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    espaces.add(espace(resultat));
                }
            }
            return espaces;
        });
    }

    public EspaceDeCompte obtenir(Compte compte, UUID espaceId) {
        exiger(compte, espaceId, Role.LECTEUR);
        return base.executer(new Contexte(compte.id(), null, espaceId),
                connexion -> lireEspace(connexion, espaceId).orElseThrow(() -> new AccesRefuse(MotifRefus.NON_MEMBRE)));
    }

    // ------------------------------------------------------------------ membres

    public List<Membre> membres(Compte compte, UUID espaceId) {
        exiger(compte, espaceId, Role.LECTEUR);
        return base.executer(new Contexte(compte.id(), null, espaceId), connexion -> lireMembres(connexion, espaceId));
    }

    /** Change le rôle d'un membre ; réservé aux administrateurs de l'espace (ACC-03-S2). */
    public Membre changerRole(Compte compte, UUID espaceId, UUID cibleId, Role nouveauRole) {
        if (nouveauRole == null) {
            throw new DonneeInvalide("Rôle obligatoire : LECTEUR, CONTRIBUTEUR ou ADMINISTRATEUR.");
        }
        exiger(compte, espaceId, Role.ADMINISTRATEUR);
        return base.executer(new Contexte(compte.id(), null, espaceId), connexion -> {
            verrouillerEspace(connexion, espaceId);
            Role actuel = roleDe(connexion, espaceId, cibleId);
            if (actuel == Role.ADMINISTRATEUR && nouveauRole != Role.ADMINISTRATEUR) {
                verifierAutreAdministrateur(connexion, espaceId);
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "UPDATE appartenance SET role = ? WHERE espace_id = ? AND compte_id = ?")) {
                requete.setString(1, nouveauRole.name());
                requete.setObject(2, espaceId);
                requete.setObject(3, cibleId);
                requete.executeUpdate();
            }
            return lireMembres(connexion, espaceId).stream().filter(membre -> membre.compteId().equals(cibleId)).findFirst().orElseThrow();
        });
    }

    /** Retire un membre de l'espace ; effectif dès sa requête suivante (ACC-05-S2). */
    public void revoquer(Compte compte, UUID espaceId, UUID cibleId) {
        exiger(compte, espaceId, Role.ADMINISTRATEUR);
        base.executer(new Contexte(compte.id(), null, espaceId), connexion -> {
            verrouillerEspace(connexion, espaceId);
            if (roleDe(connexion, espaceId, cibleId) == Role.ADMINISTRATEUR) {
                verifierAutreAdministrateur(connexion, espaceId);
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "DELETE FROM appartenance WHERE espace_id = ? AND compte_id = ?")) {
                requete.setObject(1, espaceId);
                requete.setObject(2, cibleId);
                requete.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ invitations

    /** Invite une adresse e-mail avec un rôle ; réservé aux administrateurs (ACC-05-S1). */
    public Invitation inviter(Compte compte, UUID espaceId, String email, Role role) {
        String adresse = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(adresse).matches()) {
            throw new DonneeInvalide("Adresse e-mail invalide.");
        }
        if (role == null) {
            throw new DonneeInvalide("Rôle obligatoire : LECTEUR, CONTRIBUTEUR ou ADMINISTRATEUR.");
        }
        exiger(compte, espaceId, Role.ADMINISTRATEUR);
        UUID id = UUID.randomUUID();
        return base.executer(new Contexte(compte.id(), null, espaceId), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT INTO invitation (id, espace_id, email, role, invite_par, expire_le)"
                            + " VALUES (?, ?, ?, ?, argos_compte_courant(), now() + make_interval(days => ?))")) {
                requete.setObject(1, id);
                requete.setObject(2, espaceId);
                requete.setString(3, adresse);
                requete.setString(4, role.name());
                requete.setInt(5, validiteJours);
                requete.executeUpdate();
            }
            return lireInvitations(connexion, "i.id = ?", id).get(0);
        });
    }

    public List<Invitation> invitations(Compte compte, UUID espaceId) {
        exiger(compte, espaceId, Role.ADMINISTRATEUR);
        return base.executer(new Contexte(compte.id(), null, espaceId),
                connexion -> lireInvitations(connexion, "i.espace_id = ?", espaceId));
    }

    public void revoquerInvitation(Compte compte, UUID espaceId, UUID invitationId) {
        exiger(compte, espaceId, Role.ADMINISTRATEUR);
        int modifiees = base.executer(new Contexte(compte.id(), null, espaceId), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "UPDATE invitation SET etat = 'REVOQUEE' WHERE id = ? AND espace_id = ? AND etat = 'EN_ATTENTE'")) {
                requete.setObject(1, invitationId);
                requete.setObject(2, espaceId);
                return requete.executeUpdate();
            }
        });
        if (modifiees == 0) {
            throw new RegleViolee("INVITATION_INACTIVE", "Aucune invitation en attente avec cet identifiant.");
        }
    }

    /** Invitations en attente adressées à l'e-mail vérifié du compte. */
    public List<Invitation> mesInvitations(Compte compte, String emailVerifie) {
        if (emailVerifie == null) {
            return List.of();
        }
        return base.executer(new Contexte(compte.id(), emailVerifie, null),
                connexion -> lireInvitations(connexion, "i.etat = 'EN_ATTENTE' AND i.expire_le > now() AND lower(i.email) = ?",
                        emailVerifie));
    }

    /**
     * Accepte une invitation adressée à l'e-mail vérifié du compte (ACC-05-S1).
     *
     * @throws Introuvable si aucune invitation adressée à cet e-mail ne porte cet identifiant : ce
     *                     n'est pas un refus d'accès à un espace, il n'est donc pas journalisé
     * @throws RegleViolee {@code INVITATION_INACTIVE} si l'invitation est déjà acceptée ou révoquée,
     *                     {@code INVITATION_EXPIREE} si elle a expiré, {@code DEJA_MEMBRE} si le compte
     *                     est déjà membre de l'espace
     */
    public EspaceDeCompte accepter(Compte compte, String emailVerifie, UUID invitationId) {
        if (emailVerifie == null) {
            throw new Introuvable("Aucune invitation ne vous est adressée avec cet identifiant.");
        }
        Contexte contexte = new Contexte(compte.id(), emailVerifie, null);
        Invitation invitation = base.executer(contexte, connexion -> lireInvitations(connexion, "i.id = ?", invitationId))
                .stream().filter(candidate -> emailVerifie.equalsIgnoreCase(candidate.email()))
                .findFirst().orElseThrow(() -> new Introuvable("Aucune invitation ne vous est adressée avec cet identifiant."));
        if (!"EN_ATTENTE".equals(invitation.etat())) {
            throw new RegleViolee("INVITATION_INACTIVE", "L'invitation a déjà été acceptée ou révoquée.");
        }
        if (!invitation.expireLe().isAfter(OffsetDateTime.now())) {
            throw new RegleViolee("INVITATION_EXPIREE", "L'invitation a expiré.");
        }
        UUID espaceId = invitation.espaceId();
        return base.executer(contexte.dansEspace(espaceId), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT 1 FROM appartenance WHERE espace_id = ? AND compte_id = argos_compte_courant()")) {
                requete.setObject(1, espaceId);
                try (ResultSet resultat = requete.executeQuery()) {
                    if (resultat.next()) {
                        throw new RegleViolee("DEJA_MEMBRE", "Vous êtes déjà membre de cet espace.");
                    }
                }
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT INTO appartenance (espace_id, compte_id, role) VALUES (?, argos_compte_courant(), ?)")) {
                requete.setObject(1, espaceId);
                requete.setString(2, invitation.role().name());
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "UPDATE invitation SET etat = 'ACCEPTEE', destinataire_id = argos_compte_courant()"
                            + " WHERE id = ? AND etat = 'EN_ATTENTE'")) {
                requete.setObject(1, invitationId);
                if (requete.executeUpdate() != 1) {
                    throw new RegleViolee("INVITATION_INACTIVE", "L'invitation n'est plus en attente.");
                }
            }
            return lireEspace(connexion, espaceId).orElseThrow();
        });
    }

    // ------------------------------------------------------------------ lecture

    private static Optional<EspaceDeCompte> lireEspace(Connection connexion, UUID espaceId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT e.id, e.nom, a.role, e.cree_le FROM espace e"
                        + " JOIN appartenance a ON a.espace_id = e.id AND a.compte_id = argos_compte_courant()"
                        + " WHERE e.id = ?")) {
            requete.setObject(1, espaceId);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? Optional.of(espace(resultat)) : Optional.empty();
            }
        }
    }

    private static EspaceDeCompte espace(ResultSet resultat) throws SQLException {
        return new EspaceDeCompte(resultat.getObject(1, UUID.class), resultat.getString(2), Role.valueOf(resultat.getString(3)),
                resultat.getObject(4, OffsetDateTime.class));
    }

    private static List<Membre> lireMembres(Connection connexion, UUID espaceId) throws SQLException {
        List<Membre> membres = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT a.compte_id, co.nom_affiche, a.role, a.depuis FROM appartenance a"
                        + " JOIN compte co ON co.id = a.compte_id WHERE a.espace_id = ? ORDER BY co.nom_affiche, a.compte_id")) {
            requete.setObject(1, espaceId);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    membres.add(new Membre(resultat.getObject(1, UUID.class), resultat.getString(2), Role.valueOf(resultat.getString(3)),
                            resultat.getObject(4, OffsetDateTime.class)));
                }
            }
        }
        return membres;
    }

    private static List<Invitation> lireInvitations(Connection connexion, String filtre, Object valeur) throws SQLException {
        List<Invitation> invitations = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT i.id, i.espace_id, i.email, i.role, i.etat, co.nom_affiche, i.cree_le, i.expire_le"
                        + " FROM invitation i JOIN compte co ON co.id = i.invite_par WHERE " + filtre
                        + " ORDER BY i.cree_le, i.id")) {
            requete.setObject(1, valeur);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    invitations.add(new Invitation(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                            resultat.getString(3), Role.valueOf(resultat.getString(4)), resultat.getString(5), resultat.getString(6),
                            resultat.getObject(7, OffsetDateTime.class), resultat.getObject(8, OffsetDateTime.class)));
                }
            }
        }
        return invitations;
    }

    private static Role roleDe(Connection connexion, UUID espaceId, UUID compteId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT role FROM appartenance WHERE espace_id = ? AND compte_id = ?")) {
            requete.setObject(1, espaceId);
            requete.setObject(2, compteId);
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    throw new RegleViolee("MEMBRE_INCONNU", "Ce compte n'est pas membre de l'espace.");
                }
                return Role.valueOf(resultat.getString(1));
            }
        }
    }

    /**
     * Verrouille la ligne de l'espace jusqu'à la fin de la transaction : deux changements de rôle ou
     * retraits concurrents dans le même espace s'exécutent l'un après l'autre, et le second compte
     * les administrateurs après la validation du premier (INV-1 sûr en concurrence).
     */
    private static void verrouillerEspace(Connection connexion, UUID espaceId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("SELECT 1 FROM espace WHERE id = ? FOR UPDATE")) {
            requete.setObject(1, espaceId);
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    throw new AccesRefuse(MotifRefus.NON_MEMBRE);
                }
            }
        }
    }

    /** INV-1 : un espace garde toujours au moins un administrateur. */
    private static void verifierAutreAdministrateur(Connection connexion, UUID espaceId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT count(*) FROM appartenance WHERE espace_id = ? AND role = 'ADMINISTRATEUR'")) {
            requete.setObject(1, espaceId);
            try (ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                if (resultat.getLong(1) <= 1) {
                    throw new RegleViolee("DERNIER_ADMINISTRATEUR",
                            "Un espace garde toujours au moins un administrateur.");
                }
            }
        }
    }
}
