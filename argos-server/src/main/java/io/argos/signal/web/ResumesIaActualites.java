package io.argos.signal.web;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.aigateway.DemandeIa;
import io.argos.aigateway.EntreeIa;
import io.argos.aigateway.PasserelleIa;
import io.argos.aigateway.RegistrePrompts;
import io.argos.aigateway.ReponseIa;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;

/**
 * Résumé IA d'une actualité de la veille web, demandé explicitement par l'utilisateur (tâche
 * {@code resume-actualite}, IAG-05).
 *
 * <p>L'actualité est d'abord lue sous le rôle {@code argos_app} avec le compte de l'appelant : une
 * actualité invisible n'est jamais résumée. Seuls son titre et son texte collecté, contenus publics
 * non fiables (R-11), sont transmis à la passerelle, qui les balise comme données. L'appel est imputé
 * à l'espace de la ligne restituée (une actualité personnelle, au seul plafond de l'instance) et au
 * quota journalier du compte demandeur ({@code argos.ia.quota-compte-jour}) : un compte ne peut pas
 * épuiser seul le plafond de l'instance. Sans résultat IA (refus, plafond, panne, IA désactivée), la
 * réponse reprend le résumé extractif déterministe : la veille ne dépend jamais de l'IA.
 *
 * <p>Un résumé produit est gardé en mémoire, borné en nombre et en durée, pour la même actualité, le
 * même contenu, la même version de prompt et le même modèle : une nouvelle demande, par le même compte
 * ou par un autre compte qui voit l'actualité, ne sollicite pas le modèle. Les sources restituées sont
 * toujours celles que voit le compte demandeur.
 */
@ApplicationScoped
public class ResumesIaActualites {

    /**
     * Résumé restitué.
     *
     * @param etat {@code DISPONIBLE}, {@code PLAFOND_ATTEINT}, {@code REFUSE} ou {@code INDISPONIBLE}
     * @param methode {@code IA_V<version>} pour un résumé produit par l'IA, sinon {@code EXTRAIT_V1}
     * @param texte résumé IA, ou extrait déterministe du contenu externe
     * @param mention « produit par l'IA » pour un résumé IA, absente sinon
     * @param sources adresses de l'actualité et des sources visibles qui l'ont citée
     */
    public record ResumeIaActualite(String etat, String methode, String texte, String mention, List<String> sources) {
    }

    @Inject
    ActualitesWeb actualites;

    @Inject
    ContexteBase base;

    @Inject
    PasserelleIa passerelle;

    @Inject
    RegistrePrompts registre;

    @ConfigProperty(name = "argos.ia.cache-actualites.taille", defaultValue = "500")
    int tailleCache;

    @ConfigProperty(name = "argos.ia.cache-actualites.duree", defaultValue = "PT24H")
    Duration dureeCache;

    /** Résumé produit gardé en mémoire. */
    private record EnCache(String methode, String texte, String mention, Instant expire) {
    }

    private final Map<String, EnCache> cache = new LinkedHashMap<>(16, 0.75f, true);

    /** Résumé de l'actualité visible ; vide si l'actualité est inconnue ou invisible pour le compte. */
    public Optional<ResumeIaActualite> resumer(UUID actualiteId, UUID compteId) {
        Optional<ActualitesWeb.Detail> detail = actualites.detail(actualiteId, compteId);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        ActualitesWeb.Actualite a = detail.get().actualite();
        List<String> sources = sources(a);
        String cle = cle(actualiteId, a);
        EnCache connu = lireCache(cle);
        if (connu != null) {
            return Optional.of(new ResumeIaActualite("DISPONIBLE", connu.methode(), connu.texte(), connu.mention(), sources));
        }
        UUID espace = espaceImpute(actualiteId, compteId);
        ReponseIa r = passerelle.demander(new DemandeIa(RegistrePrompts.RESUME_ACTUALITE, espace,
                List.of(EntreeIa.publique("titre", a.titre()),
                        EntreeIa.publique("texte", a.contenu().texte())),
                sources, compteId));
        if (r.produit()) {
            EnCache produit = new EnCache("IA_V" + r.versionPrompt(), r.champ("resume"), r.mention(),
                    Instant.now().plus(dureeCache));
            ecrireCache(cle, produit);
            return Optional.of(new ResumeIaActualite("DISPONIBLE", produit.methode(), produit.texte(), produit.mention(),
                    r.sources()));
        }
        String etat = r.plafondAtteint() ? "PLAFOND_ATTEINT"
                : r.issue() == ReponseIa.Issue.INDISPONIBLE || r.issue() == ReponseIa.Issue.DESACTIVEE ? "INDISPONIBLE"
                : "REFUSE";
        return Optional.of(new ResumeIaActualite(etat, ActualitesWeb.METHODE_RESUME,
                detail.get().resume().extrait().texte(), null, sources));
    }

    /** Clé du cache : actualité, empreinte du contenu transmis, version du prompt et modèle de la tâche. */
    private String cle(UUID actualiteId, ActualitesWeb.Actualite a) {
        int version = registre.prompt(RegistrePrompts.RESUME_ACTUALITE).map(p -> p.version()).orElse(0);
        String contenu = a.titre() + "\u0000" + a.contenu().texte();
        String empreinte;
        try {
            empreinte = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(contenu.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return actualiteId + "|" + empreinte + "|" + version + "|" + passerelle.modele(RegistrePrompts.RESUME_ACTUALITE);
    }

    private synchronized EnCache lireCache(String cle) {
        EnCache e = cache.get(cle);
        if (e != null && e.expire().isBefore(Instant.now())) {
            cache.remove(cle);
            return null;
        }
        return e;
    }

    private synchronized void ecrireCache(String cle, EnCache e) {
        cache.put(cle, e);
        while (cache.size() > Math.max(tailleCache, 0)) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private UUID espaceImpute(UUID actualiteId, UUID compteId) {
        return base.executer(new Contexte(compteId, null, null), c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT espace_id FROM actualite WHERE id = ?")) {
                ps.setObject(1, actualiteId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getObject(1, UUID.class) : null;
                }
            }
        });
    }

    private static List<String> sources(ActualitesWeb.Actualite a) {
        Set<String> liens = new LinkedHashSet<>();
        if (a.lien() != null) {
            liens.add(a.lien());
        }
        a.sources().forEach(s -> {
            if (s.lien() != null) {
                liens.add(s.lien());
            }
        });
        return new ArrayList<>(liens);
    }
}
