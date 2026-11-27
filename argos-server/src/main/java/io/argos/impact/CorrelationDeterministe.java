package io.argos.impact;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.argos.impact.ImpactsEnLot.Ecrit;
import io.argos.impact.ImpactsEnLot.Existant;
import io.argos.impact.ImpactsEnLot.Ligne;
import io.argos.impact.ImpactsEnLot.Perime;
import io.argos.impact.ImpactsEnLot.Triplet;
import io.argos.impact.TraitementImpacts.TransitionSysteme;
import io.argos.inventory.InventoryQueries;
import io.argos.signal.AffectedPackage;
import io.argos.signal.SignalQueries;

/**
 * Corrélation déterministe d'un lot de signaux (DF-COR-2, ADR-0013, COR-13) : un nombre fixe d'instructions SQL par
 * lot, quel que soit le nombre de signaux et de composants (design.md, DC-1). Appelée par {@link CorrelationEngine}
 * dans une transaction par lot.
 *
 * <p>Un nouveau calcul met à jour l'impact existant du triplet (COR-07) et ne le réécrit pas s'il ne change pas ; il
 * conserve son état de traitement (COR-09-S2). Un impact actif qui n'est plus vérifié passe {@code RESOLU} avec
 * l'auteur « système » et un {@link MotifSysteme} (COR-11) ; un impact {@code ECARTE} n'est jamais modifié par le
 * système ; un impact résolu par le système de nouveau vérifié repasse {@code OUVERT}. Un impact dont le composant a
 * quitté l'inventaire puis y revient à la même version retrouve ce composant (INV-C4).
 */
@ApplicationScoped
class CorrelationDeterministe {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Résultat de la corrélation d'un signal du lot. */
    record Resultat(UUID signalId, int created, int updated, int resolved, int reopened, List<UUID> impactIds,
            ImpactsRecorded recorded) {
    }

    @Inject
    Clock horloge;

    @Inject
    DataSource dataSource;

    @Inject
    SignalQueries signals;

    @Inject
    InventoryQueries inventories;

    /** Correspondance vérifiée entre un signal et un composant, et la ligne d'impact qu'elle produit. */
    private record Correspondance(SignalQueries.SignalView signal, InventoryQueries.ComponentMatch component,
            MatchRules.Level level, Ligne ligne) {
    }

    /** Bilan en cours de la corrélation d'un signal. */
    private static final class Bilan {
        private int created;
        private int updated;
        private int reopened;
        private int resolved;
        private final List<UUID> kept = new ArrayList<>();
        // Paquets (projet + clé) pour lesquels un composant reste affecté : un impact de l'ancienne version de ce
        // paquet est résolu comme composant retiré, et non comme version hors plage.
        private final Set<String> stillAffected = new HashSet<>();
        private final List<ImpactsRecorded.Item> items = new ArrayList<>();
    }

    /** État enregistré d'un impact après l'upsert : identifiant, état, créé ou modifié. */
    private record Enregistre(UUID id, EtatImpact etat, boolean insere, boolean modifie) {
    }

    /**
     * Corrèle les signaux donnés avec tous les projets, ou avec un seul projet, dans la transaction de l'appelant.
     *
     * <p>Un signal inconnu (supprimé depuis la constitution du lot) n'a pas de résultat ; l'appelant le constate.
     *
     * @return un résultat par signal connu, dans l'ordre des identifiants donnés
     */
    List<Resultat> correler(List<UUID> signalIds, Optional<UUID> projectId) throws SQLException {
        Map<UUID, SignalQueries.SignalView> signaux = signals.findAll(signalIds);
        if (signaux.isEmpty()) {
            return List.of();
        }
        Instant maintenant = horloge.instant();
        Map<UUID, Bilan> bilans = new LinkedHashMap<>();
        signaux.keySet().forEach(signalId -> bilans.put(signalId, new Bilan()));
        try (Connection connexion = dataSource.getConnection()) {
            ImpactsEnLot.verrouiller(connexion, List.copyOf(signaux.keySet()));
            List<Correspondance> correspondances = correspondances(signaux.values(), projectId);
            List<Ligne> lignes = correspondances.stream().map(Correspondance::ligne).toList();
            ImpactsEnLot.rattacher(connexion, lignes);
            Map<Triplet, Existant> existants = ImpactsEnLot.existants(connexion, lignes);
            Map<Triplet, Enregistre> enregistres = enregistres(ImpactsEnLot.ecrire(connexion, lignes), existants);
            Set<UUID> rouverts = rouvrir(connexion, enregistres.values(), maintenant);
            for (Correspondance correspondance : correspondances) {
                comptabiliser(correspondance, existants, enregistres, rouverts, bilans.get(correspondance.signal().id()));
            }
            resoudrePerimes(connexion, signaux, projectId, bilans, maintenant);
        }
        List<Resultat> resultats = new ArrayList<>();
        bilans.forEach((signalId, bilan) -> resultats.add(resultat(signaux.get(signalId), bilan)));
        return resultats;
    }

    private static Resultat resultat(SignalQueries.SignalView signal, Bilan bilan) {
        return new Resultat(signal.id(), bilan.created, bilan.updated, bilan.resolved, bilan.reopened,
                List.copyOf(bilan.kept), new ImpactsRecorded(signal.id(), signal.title(), signal.severity(),
                        signal.source(), signal.externalId(), bilan.items));
    }

    /** Correspondances de chaque signal avec les composants du projet (ou de tous les projets), lus en une requête. */
    private List<Correspondance> correspondances(Collection<SignalQueries.SignalView> signaux, Optional<UUID> projectId) {
        Map<UUID, Map<String, List<AffectedPackage>>> parSignal = new LinkedHashMap<>();
        Set<String> cles = new LinkedHashSet<>();
        for (SignalQueries.SignalView signal : signaux) {
            Map<String, List<AffectedPackage>> parCle = parPaquet(signal);
            parSignal.put(signal.id(), parCle);
            cles.addAll(parCle.keySet());
        }
        Map<String, List<InventoryQueries.ComponentMatch>> composantsParCle = new HashMap<>();
        for (InventoryQueries.ComponentMatch composant : inventories.componentsMatching(cles, projectId)) {
            composantsParCle.computeIfAbsent(composant.packageKey(), cle -> new ArrayList<>()).add(composant);
        }
        List<Correspondance> correspondances = new ArrayList<>();
        for (SignalQueries.SignalView signal : signaux) {
            parSignal.get(signal.id()).forEach((cle, entrees) -> {
                for (InventoryQueries.ComponentMatch composant : composantsParCle.getOrDefault(cle, List.of())) {
                    best(entrees, composant).ifPresent(meilleure -> correspondances.add(correspondance(signal, composant,
                            meilleure)));
                }
            });
        }
        return correspondances;
    }

    private static Correspondance correspondance(SignalQueries.SignalView signal, InventoryQueries.ComponentMatch composant,
            Match meilleure) {
        String provenance = provenance(signal, composant, meilleure).toString();
        return new Correspondance(signal, composant, meilleure.level(),
                ImpactsEnLot.ligne(signal.id(), signal.currentVersionId(), composant, meilleure.level(), provenance));
    }

    /** Entrées de l'avis regroupées par clé de paquet. */
    private static Map<String, List<AffectedPackage>> parPaquet(SignalQueries.SignalView signal) {
        Map<String, List<AffectedPackage>> byKey = new LinkedHashMap<>();
        for (AffectedPackage affected : signal.affected()) {
            byKey.computeIfAbsent(affected.packageKey(), key -> new ArrayList<>()).add(affected);
        }
        return byKey;
    }

    /** État de chaque triplet après l'upsert : celui renvoyé par l'écriture, sinon celui de l'impact inchangé. */
    private static Map<Triplet, Enregistre> enregistres(Map<Triplet, Ecrit> ecrits, Map<Triplet, Existant> existants) {
        Map<Triplet, Enregistre> enregistres = new HashMap<>();
        existants.forEach((triplet, existant) -> enregistres.put(triplet,
                new Enregistre(existant.id(), existant.etat(), false, false)));
        ecrits.forEach((triplet, ecrit) -> enregistres.put(triplet,
                new Enregistre(ecrit.id(), ecrit.etat(), ecrit.insere(), !ecrit.insere())));
        return enregistres;
    }

    /** Rouvre les impacts résolus par le système et de nouveau vérifiés ; une résolution humaine est respectée (J5). */
    private static Set<UUID> rouvrir(Connection connexion, Collection<Enregistre> enregistres, Instant maintenant) {
        List<UUID> resolus = enregistres.stream().filter(impact -> impact.etat() == EtatImpact.RESOLU)
                .map(Enregistre::id).toList();
        Set<UUID> parLeSysteme = TraitementImpacts.resolusParLeSysteme(connexion, resolus);
        List<TransitionSysteme> transitions = resolus.stream().filter(parLeSysteme::contains)
                .map(id -> new TransitionSysteme(id, EtatImpact.RESOLU, EtatImpact.OUVERT, MotifSysteme.DE_NOUVEAU_VERIFIE))
                .toList();
        return TraitementImpacts.systemeParLot(connexion, transitions, maintenant);
    }

    /**
     * Compte l'impact d'une correspondance dans le bilan de son signal et le publie s'il a changé. Un impact écarté
     * reste enregistré et à jour, mais n'est publié à aucune alerte (ALR-07). Seul un impact nouveau, rouvert (par le
     * système, ou par un utilisateur depuis la corrélation précédente), de niveau changé ou porté par une nouvelle
     * version de l'avis est publié : une recorrélation sans changement (import quotidien) ne produit aucune alerte,
     * notamment au titre d'un abonnement réactivé (ABO-07-S2, [PROPOSITION] PI-4).
     */
    private static void comptabiliser(Correspondance correspondance, Map<Triplet, Existant> existants,
            Map<Triplet, Enregistre> enregistres, Set<UUID> rouverts, Bilan bilan) {
        InventoryQueries.ComponentMatch composant = correspondance.component();
        Triplet triplet = correspondance.ligne().triplet();
        bilan.stillAffected.add(ImpactsEnLot.clePaquet(composant.projectId(), composant.packageKey()));
        Enregistre impact = enregistres.get(triplet);
        if (impact == null) {
            throw new IllegalStateException("impact ni écrit ni existant pour " + triplet);
        }
        bilan.kept.add(impact.id());
        if (impact.insere()) {
            bilan.created++;
        } else if (impact.modifie()) {
            bilan.updated++;
        }
        boolean rouvert = rouverts.contains(impact.id());
        if (rouvert) {
            bilan.reopened++;
        }
        if (aPublier(impact, rouvert, existants.get(triplet), correspondance)) {
            bilan.items.add(new ImpactsRecorded.Item(impact.id(), composant.projectId(), composant.workspaceId(),
                    correspondance.level(), composant.packageKey()));
        }
    }

    /** Vrai si l'impact est actif et nouveau, rouvert, de niveau changé ou porté par une autre version de l'avis. */
    private static boolean aPublier(Enregistre impact, boolean rouvert, Existant avant, Correspondance correspondance) {
        if (rouvert) {
            return true;
        }
        return EtatImpact.ACTIFS.contains(impact.etat()) && (avant == null || aChange(avant, correspondance));
    }

    /** Vrai si l'impact existant est redevenu actif, a changé de niveau ou porte une autre version de l'avis. */
    private static boolean aChange(Existant avant, Correspondance correspondance) {
        return avant.reactive() || avant.level() != correspondance.level()
                || !avant.signalVersionId().equals(correspondance.signal().currentVersionId());
    }

    /**
     * Résout les impacts actifs des signaux du lot (et du projet donné) qui ne sont plus vérifiés, avec le motif qui
     * l'explique (COR-11). Les impacts écartés et déjà résolus ne sont pas touchés.
     */
    private static void resoudrePerimes(Connection connexion, Map<UUID, SignalQueries.SignalView> signaux,
            Optional<UUID> projectId, Map<UUID, Bilan> bilans, Instant maintenant) {
        List<UUID> retenus = bilans.values().stream().flatMap(bilan -> bilan.kept.stream()).toList();
        List<Perime> perimes = ImpactsEnLot.perimes(connexion, signaux.keySet(), projectId.orElse(null), retenus);
        // Seuls les impacts sans composant d'un paquet qui n'est plus affecté demandent si le paquet reste présent.
        List<Perime> aVerifier = perimes.stream()
                .filter(perime -> perime.componentId() == null && !signaux.get(perime.signalId()).affected().isEmpty()
                        && !bilans.get(perime.signalId()).stillAffected
                                .contains(ImpactsEnLot.clePaquet(perime.projectId(), perime.packageKey())))
                .toList();
        Set<String> presents = ImpactsEnLot.paquetsPresents(connexion, aVerifier);
        List<TransitionSysteme> transitions = new ArrayList<>();
        for (Perime perime : perimes) {
            MotifSysteme motif = motif(perime, signaux.get(perime.signalId()), bilans.get(perime.signalId()), presents);
            transitions.add(new TransitionSysteme(perime.id(), perime.etat(), EtatImpact.RESOLU, motif));
        }
        Set<UUID> resolus = TraitementImpacts.systemeParLot(connexion, transitions, maintenant);
        for (Perime perime : perimes) {
            if (resolus.contains(perime.id())) {
                bilans.get(perime.signalId()).resolved++;
            }
        }
    }

    private static MotifSysteme motif(Perime perime, SignalQueries.SignalView signal, Bilan bilan, Set<String> presents) {
        String paquet = ImpactsEnLot.clePaquet(perime.projectId(), perime.packageKey());
        if (signal.affected().isEmpty()) {
            // Avis retiré par sa source (OSV « withdrawn ») : il ne déclare plus aucun paquet affecté.
            return MotifSysteme.AVIS_RETIRE;
        }
        if (perime.componentId() != null) {
            return MotifSysteme.AVIS_MODIFIE;
        }
        if (!bilan.stillAffected.contains(paquet) && presents.contains(paquet)) {
            return MotifSysteme.VERSION_HORS_PLAGE;
        }
        return MotifSysteme.COMPOSANT_RETIRE;
    }

    private record Match(MatchRules.Level level, AffectedPackage affected) {
    }

    /** Niveau le plus fort parmi les entrées de l'avis pour ce paquet. */
    private static Optional<Match> best(List<AffectedPackage> candidates, InventoryQueries.ComponentMatch component) {
        Match best = null;
        for (AffectedPackage candidate : candidates) {
            Optional<MatchRules.Level> level = MatchRules.evaluate(candidate, component.version(), !component.inventoryObsolete());
            if (level.isPresent() && (best == null || level.get().ordinal() < best.level().ordinal())) {
                best = new Match(level.get(), candidate);
            }
        }
        return Optional.ofNullable(best);
    }

    private static ObjectNode provenance(SignalQueries.SignalView signal, InventoryQueries.ComponentMatch component, Match match) {
        ObjectNode provenance = MAPPER.createObjectNode();
        provenance.put("signalSource", signal.source());
        provenance.put("signalExternalId", signal.externalId());
        provenance.put("signalVersionId", signal.currentVersionId().toString());
        provenance.put("packageKey", component.packageKey());
        provenance.put("componentPurl", component.purl());
        provenance.put("componentVersion", component.version());
        provenance.put("declaredPurl", match.affected().declaredPurl());
        provenance.set("affectedRanges", MAPPER.valueToTree(match.affected().ranges()));
        provenance.set("affectedVersions", MAPPER.valueToTree(match.affected().versions()));
        provenance.put("inventoryOrigin", component.inventoryOrigin());
        provenance.put("inventoryDate", component.inventoryDate() == null ? null : component.inventoryDate().toString());
        provenance.put("inventoryObsolete", component.inventoryObsolete());
        provenance.put("method", MatchRules.METHOD);
        provenance.put("strategy", MatchRules.STRATEGY);
        return provenance;
    }
}
