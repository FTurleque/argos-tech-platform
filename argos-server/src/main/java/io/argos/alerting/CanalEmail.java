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
        } catch (RuntimeException e) {
            throw new EchecDiffusion("envoi SMTP impossible : " + e.getClass().getSimpleName(), false, e);
        }
    }

    /** Sujet : nature, gravité et titre. */
    static String sujet(MessageAlerte m) {
        String prefixe = switch (m.nature()) {
            case "DAILY" -> "[Argos] Résumé quotidien";
            case "WEEKLY" -> "[Argos] Résumé hebdomadaire";
            case "RAPPEL" -> "[Argos] Rappel d'échéance";
            case "ANNONCE" -> "[Argos] Standard modifié";
            default -> "[Argos] Alerte" + (m.gravite() == null ? "" : " " + m.gravite());
        };
        // Le titre vient d'une source externe : aucun retour à la ligne ne doit atteindre l'en-tête.
        return (prefixe + " : " + m.titre()).replaceAll("[\r\n\t]+", " ").strip();
    }

    /** Corps texte : pour chaque élément, titre, gravité, projets, sources et lien ; puis l'état du résumé IA. */
    static String corps(MessageAlerte m) {
        StringBuilder b = new StringBuilder();
        ContenuAlerte c = m.contenu();
        // Rappels et annonces du radar : aucune gravité, projets cités avec la version utilisée.
        boolean radar = "RAPPEL".equals(m.nature()) || "ANNONCE".equals(m.nature());
        if ("IMMEDIATE".equals(m.nature())) {
            if (c.resumeIa() != null) {
                b.append("Résumé (produit par IA, à vérifier sur les sources) :\n").append(c.resumeIa()).append("\n\n");
            } else {
                b.append("Résumé IA non disponible.\n\n");
            }
        }
        for (ContenuAlerte.Element e : c.elements()) {
            b.append("• ").append(e.titre()).append('\n');
            if (e.gravite() != null || !radar) {
                b.append("  Gravité : ").append(e.gravite() == null ? "non précisée" : e.gravite()).append('\n');
            }
            b.append("  Projets : ").append(e.projets().stream()
                    .map(p -> p.nom() + " (" + p.niveau() + ")").collect(Collectors.joining(", "))).append('\n');
            b.append("  Sources : ").append(e.sources().stream()
                    .map(s -> s.nom() + " " + s.identifiant() + (s.url() == null ? "" : " <" + s.url() + ">"))
                    .collect(Collectors.joining(", "))).append('\n');
            b.append("  Voir dans Argos : ").append(e.lien()).append("\n\n");
        }
        return b.toString();
    }
}
