package io.argos.catalog.profil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Catalogue des profils de veille (ADR-0024 point 1, exigences PRF-03 et PRF-04).
 *
 * <p>Chaque identifiant listé dans {@code argos.catalogue.profils.manifestes} correspond au
 * manifeste {@code catalog/profils/<identifiant>.json}. Tous les manifestes sont validés au
 * démarrage : un manifeste invalide empêche le serveur de démarrer plutôt que de publier un
 * profil incohérent. Un profil listé dans {@code argos.catalogue.profils.retires} n'est plus publié.
 *
 * <p>Publier un profil revient à ajouter un manifeste : aucun autre profil n'est modifié (PRF-04).
 * Ne connaît pas les préférences des utilisateurs.
 */
@ApplicationScoped
public class CatalogueProfils {

    static final String DOSSIER = "catalog/profils/";

    private final Map<String, ManifesteProfil> declares;
    private final List<String> ordre;
    private final Set<String> retires;

    @Inject
    public CatalogueProfils(ObjectMapper mapper,
            @ConfigProperty(name = "argos.catalogue.profils.manifestes") List<String> manifestes,
            @ConfigProperty(name = "argos.catalogue.profils.retires") Optional<Set<String>> retires) {
        this(lire(mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES), manifestes),
                retires.orElse(Set.of()));
    }

    CatalogueProfils(List<ManifesteProfil> manifestes, Set<String> retires) {
        Map<String, ManifesteProfil> parIdentifiant = new LinkedHashMap<>();
        for (ManifesteProfil manifeste : manifestes) {
            List<String> erreurs = ValidateurManifeste.erreurs(manifeste);
            if (!erreurs.isEmpty()) {
                throw new ManifesteInvalideException(String.valueOf(manifeste.identifiant()), erreurs);
            }
            if (parIdentifiant.putIfAbsent(manifeste.identifiant(), manifeste) != null) {
                throw new ManifesteInvalideException(manifeste.identifiant(), List.of("profil déclaré deux fois"));
            }
        }
        this.declares = Map.copyOf(parIdentifiant);
        this.ordre = List.copyOf(parIdentifiant.keySet());
        this.retires = Set.copyOf(retires);
    }

    /** Profils publiés, dans l'ordre de déclaration. */
    public List<ManifesteProfil> publies() {
        return ordre.stream().filter(id -> !retires.contains(id)).map(declares::get).toList();
    }

    /** Manifeste d'un profil publié. */
    public Optional<ManifesteProfil> trouver(String identifiant) {
        if (identifiant == null || retires.contains(identifiant)) {
            return Optional.empty();
        }
        return Optional.ofNullable(declares.get(identifiant));
    }

    /** Vrai si le profil est déclaré et non retiré. */
    public boolean estPublie(String identifiant) {
        return trouver(identifiant).isPresent();
    }

    static List<ManifesteProfil> lire(ObjectMapper mapper, List<String> identifiants) {
        List<ManifesteProfil> manifestes = new ArrayList<>();
        ClassLoader chargeur = Thread.currentThread().getContextClassLoader();
        for (String identifiant : identifiants) {
            String ressource = DOSSIER + identifiant + ".json";
            try (InputStream flux = chargeur.getResourceAsStream(ressource)) {
                if (flux == null) {
                    throw new ManifesteInvalideException(identifiant, List.of("ressource absente : " + ressource));
                }
                ManifesteProfil manifeste = mapper.readValue(flux, ManifesteProfil.class);
                if (!identifiant.equals(manifeste.identifiant())) {
                    throw new ManifesteInvalideException(identifiant,
                            List.of("l'identifiant du manifeste (" + manifeste.identifiant() + ") diffère du nom du fichier"));
                }
                manifestes.add(manifeste);
            } catch (IOException erreur) {
                throw new ManifesteInvalideException(identifiant, List.of("lecture impossible : " + erreur.getMessage()));
            }
        }
        return manifestes;
    }
}
