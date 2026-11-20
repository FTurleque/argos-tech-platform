package io.argos.alerting;

import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

/**
 * Canal {@code EMAIL} : envoi par Quarkus Mailer vers le relais SMTP du Synology (Q-1), configuré par
 * les variables d'environnement {@code ARGOS_SMTP_*} et {@code ARGOS_MAIL_FROM}. Le message ne
 * contient que les données du contenu, déjà restreintes au destinataire ; aucun secret n'y figure.
 */
@ApplicationScoped
public class CanalEmail implements CanalDiffusion {

    @Inject
    Mailer mailer;

    @Override
    public String code() {
        return "EMAIL";
    }

    @Override
    public void diffuser(MessageAlerte message) throws EchecDiffusion {
        if (message.adresse() == null || message.adresse().isBlank()) {
            throw new EchecDiffusion("aucune adresse e-mail pour le destinataire", true, null);
        }
        try {
            mailer.send(Mail.withText(message.adresse(), sujet(message), corps(message)));
        } catch (RuntimeException erreur) {
            throw new EchecDiffusion("envoi SMTP impossible : " + erreur.getClass().getSimpleName(), false, erreur);
        }
    }

    /** Sujet : nature, gravité et titre. */
    static String sujet(MessageAlerte message) {
        String prefixe = switch (message.nature()) {
            case "DAILY" -> "[Argos] Résumé quotidien";
            case "WEEKLY" -> "[Argos] Résumé hebdomadaire";
            case "RAPPEL" -> "[Argos] Rappel d'échéance";
            case "ANNONCE" -> "[Argos] Standard modifié";
            default -> "[Argos] Alerte" + (message.gravite() == null ? "" : " " + message.gravite());
        };
        // Le titre vient d'une source externe : aucun retour à la ligne ne doit atteindre l'en-tête.
        return (prefixe + " : " + message.titre()).replaceAll("[\r\n\t]+", " ").strip();
    }

    /** Corps texte : pour chaque élément, titre, gravité, projets, sources et lien ; puis l'état du résumé IA. */
    static String corps(MessageAlerte message) {
        StringBuilder texte = new StringBuilder();
        ContenuAlerte contenu = message.contenu();
        // Rappels et annonces du radar : aucune gravité, projets cités avec la version utilisée.
        boolean radar = "RAPPEL".equals(message.nature()) || "ANNONCE".equals(message.nature());
        if ("IMMEDIATE".equals(message.nature())) {
            if (contenu.resumeIa() != null) {
                texte.append("Résumé (").append(contenu.mentionResumeIa()).append(", à vérifier sur les sources) :\n")
                        .append(contenu.resumeIa()).append("\n\n");
            } else {
                texte.append("Résumé IA non disponible.\n\n");
            }
        }
        for (ContenuAlerte.Element element : contenu.elements()) {
            texte.append("• ").append(element.titre()).append('\n');
            if (element.gravite() != null || !radar) {
                texte.append("  Gravité : ").append(element.gravite() == null ? "non précisée" : element.gravite()).append('\n');
            }
            texte.append("  Projets : ").append(element.projets().stream()
                    .map(projet -> projet.nom() + " (" + projet.niveau() + ")").collect(Collectors.joining(", "))).append('\n');
            texte.append("  Sources : ").append(element.sources().stream()
                    .map(source -> source.nom() + " " + source.identifiant() + (source.url() == null ? "" : " <" + source.url() + ">"))
                    .collect(Collectors.joining(", "))).append('\n');
            texte.append("  Voir dans Argos : ").append(element.lien()).append("\n\n");
        }
        return texte.toString();
    }
}
