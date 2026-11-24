package io.argos.signal.opportunite;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.ingestion.culture.LecteurDataCulture;
import io.argos.ingestion.web.LecteurFlux;
import io.argos.ingestion.web.LecteurPage;
import io.argos.ingestion.web.RecuperateurWeb;
import io.argos.ingestion.web.RecuperationRefusee;
import io.argos.observabilite.TacheObservee;
import io.quarkus.scheduler.Scheduled;

/**
 * Collecte des sources d'un profil de veille (ART-05, Q-1).
 *
 * <ul>
 *   <li>une source dont les conditions d'usage ne sont pas enregistrées n'est pas collectée : aucune
 *       requête n'est émise et son état reste {@code CONDITIONS_A_VERIFIER} (ART-05-S1) ;</li>
 *   <li>une source vérifiée est récupérée par le collecteur de la veille web ({@link RecuperateurWeb} :
 *       adresse publique, redirections vérifiées, durée et taille bornées) puis lue comme réponse de
 *       l'API data.culture.gouv.fr ({@link LecteurDataCulture}) ou comme flux RSS/Atom ({@link LecteurFlux}) ;</li>
 *   <li>les disciplines et territoires lus sont ramenés aux listes du profil ; à défaut, ceux déclarés
 *       pour la source s'appliquent ; un flux RSS ne porte pas de date limite structurée : ses éléments
 *       sont enregistrés « date à confirmer » ;</li>
 *   <li>une opportunité est dédupliquée par source et par empreinte de son identifiant : une seconde
 *       collecte met à jour l'existante.</li>
 * </ul>
 *
 * <p>Le motif enregistré pour un échec est le motif générique du refus ; le détail n'est écrit que dans
 * le journal applicatif.
 */
@ApplicationScoped
public class CollecteOpportunites {

    private static final Logger LOG = Logger.getLogger(CollecteOpportunites.class);

    /** Résultat de la collecte d'une source. */
    public record Rapport(UUID sourceId, String etat, int nouvelles, int misesAJour, String erreur) {
    }

    @Inject
    Clock horloge;

    @Inject
    DataSource dataSource;

    @Inject
    SourcesProfil sources;

    @Inject
    CatalogueProfils catalogue;

    @Inject
    RecuperateurWeb recuperateur;

    @Inject
    LecteurFlux lecteurFlux;

    @ConfigProperty(name = "argos.profil-artiste.elements-max", defaultValue = "500")
    int elementsMax;

    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.identifiant")
    String champIdentifiant;
    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.titre")
    String champTitre;
    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.organisme")
    String champOrganisme;
    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.criteres")
    String champCriteres;
    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.lien")
    String champLien;
    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.discipline")
    String champDiscipline;
    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.territoire")
    String champTerritoire;
    @ConfigProperty(name = "argos.profil-artiste.data-culture.champ.date-limite")
    String champDateLimite;

    @TacheObservee
    @Scheduled(identity = "collecte-profil-artiste", cron = "{argos.profil-artiste.collecte.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        collecter("artiste", horloge.instant());
    }

    /** Collecte chaque source du profil ; une source en échec n'interrompt pas les autres. */
    public List<Rapport> collecter(String profil, Instant maintenant) {
        List<Rapport> rapports = new ArrayList<>();
        for (SourceProfil source : sources.lister(profil)) {
            rapports.add(collecter(source, maintenant));
        }
        return rapports;
    }

    /** Collecte une source ; une source non vérifiée n'est pas collectée (ART-05-S1). */
    public Rapport collecter(SourceProfil source, Instant maintenant) {
        if (!source.conditionsVerifiees()) {
            LOG.infof("source %s du profil %s non collectée : conditions d'usage à vérifier", source.id(), source.profil());
            return new Rapport(source.id(), SourceProfil.CONDITIONS_A_VERIFIER, 0, 0, null);
        }
        ManifesteProfil profil = catalogue.trouver(source.profil()).orElse(null);
        if (profil == null) {
            return new Rapport(source.id(), source.etat(), 0, 0, "Profil absent du catalogue : source non collectée.");
        }
        try {
            if (SourceProfil.DATA_CULTURE.equals(source.type()) && !sources.hoteDataCulture(URI.create(source.adresse()))) {
                throw new RecuperationRefusee(RecuperationRefusee.Motif.ADRESSE_INTERDITE, "hôte non admis");
            }
            RecuperateurWeb.Contenu contenu = recuperateur.recuperer(URI.create(source.adresse()));
            List<OpportuniteLue> lus = lire(source, profil, contenu);
            int[] bilan = enregistrer(source, lus, maintenant);
            etat(source.id(), SourceProfil.ACTIVE, null, maintenant);
            return new Rapport(source.id(), SourceProfil.ACTIVE, bilan[0], bilan[1], null);
        } catch (RecuperationRefusee refus) {
            LOG.infof(refus, "collecte de la source %s en échec : %s (%s)", source.id(), refus.motif(), refus.getMessage());
            etat(source.id(), SourceProfil.EN_ERREUR, refus.motifPublic(), maintenant);
            return new Rapport(source.id(), SourceProfil.EN_ERREUR, 0, 0, refus.motifPublic());
        } catch (LecteurDataCulture.ReponseInvalide erreur) {
            String motif = "REPONSE_INVALIDE : la réponse n'est pas celle attendue de l'API data.culture.gouv.fr.";
            LOG.infof(erreur, "collecte de la source %s en échec : %s", source.id(), erreur.getMessage());
            etat(source.id(), SourceProfil.EN_ERREUR, motif, maintenant);
            return new Rapport(source.id(), SourceProfil.EN_ERREUR, 0, 0, motif);
        } catch (IllegalStateException erreur) {
            String motif = "ERREUR_INTERNE : la collecte a été interrompue.";
            LOG.errorf(erreur, "collecte de la source %s interrompue", source.id());
            etat(source.id(), SourceProfil.EN_ERREUR, motif, maintenant);
            return new Rapport(source.id(), SourceProfil.EN_ERREUR, 0, 0, motif);
        }
    }

    /** Élément lu et ramené aux listes du profil. */
    record OpportuniteLue(String identifiant, String titre, String organisme, String criteres, String lien,
            List<String> disciplines, List<String> territoires, LocalDate dateLimite) {
    }

    List<OpportuniteLue> lire(SourceProfil source, ManifesteProfil profil, RecuperateurWeb.Contenu contenu) {
        List<OpportuniteLue> lus = new ArrayList<>();
        if (SourceProfil.DATA_CULTURE.equals(source.type())) {
            LecteurDataCulture.Champs champs = new LecteurDataCulture.Champs(champIdentifiant, champTitre,
                    champOrganisme, champCriteres, champLien, champDiscipline, champTerritoire, champDateLimite);
            for (LecteurDataCulture.Enregistrement enregistrement : LecteurDataCulture.lire(contenu.octets(), champs, elementsMax)) {
                lus.add(new OpportuniteLue(enregistrement.identifiant(), enregistrement.titre(), enregistrement.organisme(),
                        enregistrement.criteres(), CorrespondanceListes.lienSur(enregistrement.lien()),
                        ouDeclarees(CorrespondanceListes.identifiants(enregistrement.disciplines(), profil.liste("discipline")),
                                source.disciplines()),
                        ouDeclarees(CorrespondanceListes.identifiants(enregistrement.territoires(), profil.liste("territoire")),
                                source.territoires()),
                        enregistrement.dateLimite()));
            }
            return lus;
        }
        List<LecteurFlux.ElementFlux> elements = lecteurFlux.lire(contenu.octets()).elements();
        for (LecteurFlux.ElementFlux element : elements.size() > elementsMax ? elements.subList(0, elementsMax) : elements) {
            String lien = CorrespondanceListes.lienSur(element.lien());
            String identifiant = lien != null ? lien : element.identifiant() != null ? element.identifiant() : element.titre();
            lus.add(new OpportuniteLue(identifiant, element.titre(), source.libelle(), element.texte() == null ? "" : element.texte(), lien,
                    source.disciplines(), source.territoires(), null));
        }
        return lus;
    }

    private static List<String> ouDeclarees(List<String> lues, List<String> declarees) {
        return lues.isEmpty() ? declarees : lues;
    }

    /** Insère ou met à jour les opportunités ; renvoie {nouvelles, mises à jour}. */
    private int[] enregistrer(SourceProfil source, List<OpportuniteLue> lus, Instant maintenant) {
        int nouvelles = 0;
        int misesAJour = 0;
        String sql = """
                INSERT INTO opportunite (id, source_id, empreinte, rubrique, titre, organisme, criteres, lien,
                    disciplines, territoires, date_limite, recu_le, modifie_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, empreinte) DO UPDATE SET rubrique = excluded.rubrique, titre = excluded.titre,
                    organisme = excluded.organisme, criteres = excluded.criteres, lien = excluded.lien,
                    disciplines = excluded.disciplines, territoires = excluded.territoires,
                    date_limite = excluded.date_limite, modifie_le = excluded.modifie_le
                RETURNING (xmax = 0)
                """;
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            for (OpportuniteLue element : lus) {
                requete.setObject(1, UUID.randomUUID());
                requete.setObject(2, source.id());
                requete.setString(3, LecteurPage.empreinte("opportunite:" + element.identifiant()));
                requete.setString(4, source.rubrique());
                requete.setString(5, element.titre());
                requete.setString(6, element.organisme());
                requete.setString(7, element.criteres());
                requete.setString(8, element.lien());
                requete.setArray(9, connexion.createArrayOf("varchar", element.disciplines().toArray()));
                requete.setArray(10, connexion.createArrayOf("varchar", element.territoires().toArray()));
                requete.setObject(11, element.dateLimite());
                requete.setObject(12, maintenant.atOffset(ZoneOffset.UTC));
                requete.setObject(13, maintenant.atOffset(ZoneOffset.UTC));
                try (ResultSet resultat = requete.executeQuery()) {
                    if (resultat.next() && resultat.getBoolean(1)) {
                        nouvelles++;
                    } else {
                        misesAJour++;
                    }
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement des opportunités impossible", erreur);
        }
        return new int[] {nouvelles, misesAJour};
    }

    private void etat(UUID source, String etat, String erreur, Instant maintenant) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                UPDATE source_profil SET etat = ?, derniere_erreur = ?, derniere_collecte_le = ?
                WHERE id = ? AND conditions_verifiees_le IS NOT NULL
                """)) {
            requete.setString(1, etat);
            requete.setString(2, erreur == null ? null : erreur.length() > 1000 ? erreur.substring(0, 1000) : erreur);
            requete.setObject(3, maintenant.atOffset(ZoneOffset.UTC));
            requete.setObject(4, source);
            requete.executeUpdate();
        } catch (SQLException echec) {
            throw new IllegalStateException("mise à jour de l'état de la source impossible", echec);
        }
    }
}
