package io.argos.releaseradar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.catalog.standard.ReferentielStandardsEnBase;

/**
 * Calendrier des sorties issu de la copie locale d'endoflife.date ({@link CyclesDeVie}). Une
 * technologie (clé PURL) est reliée à un produit endoflife.date par le référentiel des espaces
 * demandés, et seulement par eux.
 *
 * <p>Échéances produites pour chaque cycle : {@code SORTIE} (date de sortie de la branche),
 * {@code CORRECTIF} (date de la dernière version publiée) et {@code FIN_DE_SUPPORT}. Une version
 * appartient au cycle le plus long dont elle est égale ou qu'elle prolonge par « . » ou « - »
 * (version {@code 14.2} → cycle {@code 14}).
 */
@ApplicationScoped
public class CalendrierEndoflife implements CalendrierSorties {

    /** Identifiant court de la source, cité avec chaque échéance. */
    public static final String SOURCE = "endoflife.date";

    private record LigneCycle(String produit, String cycle, LocalDate sortie, LocalDate finSupport,
            String derniereVersion, LocalDate derniereVersionLe) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    ReferentielStandardsEnBase referentiel;

    @Override
    public List<Echeance> echeances(Collection<String> cles, Set<UUID> espaces, LocalDate depuis, LocalDate jusqua) {
        Map<String, Set<String>> produitsParCle = produits(cles, espaces);
        Set<Echeance> echeances = new LinkedHashSet<>();
        Map<String, List<LigneCycle>> cycles = cycles(produitsParCle.values().stream().flatMap(Set::stream).toList());
        produitsParCle.forEach((cle, produits) -> {
            for (String produit : produits) {
                for (LigneCycle l : cycles.getOrDefault(produit, List.of())) {
                    ajouter(echeances, cle, l.cycle(), Nature.SORTIE, l.sortie(), null, depuis, jusqua);
                    if (l.derniereVersion() != null) {
                        ajouter(echeances, cle, l.derniereVersion(), Nature.CORRECTIF, l.derniereVersionLe(),
                                "Dernière version de la branche " + l.cycle(), depuis, jusqua);
                    }
                    ajouter(echeances, cle, l.cycle(), Nature.FIN_DE_SUPPORT, l.finSupport(), null, depuis, jusqua);
                }
            }
        });
        return echeances.stream().sorted(Comparator.comparing(Echeance::date).thenComparing(Echeance::cle)
                .thenComparing(Echeance::version)).toList();
    }

    @Override
    public Optional<Echeance> finDeSupport(String cle, String version, Set<UUID> espaces) {
        if (cle == null || version == null || version.isBlank()) {
            return Optional.empty();
        }
        Map<String, Set<String>> produitsParCle = produits(List.of(cle), espaces);
        Set<String> produits = produitsParCle.getOrDefault(cle, Set.of());
        Map<String, List<LigneCycle>> cycles = cycles(produits);
        LigneCycle meilleur = null;
        for (String produit : produits) {
            for (LigneCycle l : cycles.getOrDefault(produit, List.of())) {
                if (appartient(version, l.cycle()) && (meilleur == null || l.cycle().length() > meilleur.cycle().length())) {
                    meilleur = l;
                }
            }
        }
        if (meilleur == null || meilleur.finSupport() == null) {
            return Optional.empty();
        }
        return Optional.of(new Echeance(cle, meilleur.cycle(), Nature.FIN_DE_SUPPORT, meilleur.finSupport(), SOURCE, null));
    }

    /** Vrai si la version appartient au cycle : égale, ou prolongée par « . » ou « - ». */
    public static boolean appartient(String version, String cycle) {
        String v = version.strip();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v.equals(cycle) || v.startsWith(cycle + ".") || v.startsWith(cycle + "-");
    }

    private static void ajouter(Set<Echeance> echeances, String cle, String version, Nature nature, LocalDate date,
            String note, LocalDate depuis, LocalDate jusqua) {
        if (date != null && !date.isBefore(depuis) && !date.isAfter(jusqua)) {
            echeances.add(new Echeance(cle, version, nature, date, SOURCE, note));
        }
    }

    /** Produits endoflife.date reliés à chaque clé par le référentiel des espaces donnés. */
    private Map<String, Set<String>> produits(Collection<String> cles, Set<UUID> espaces) {
        Map<String, Set<String>> resultat = new HashMap<>();
        if (cles.isEmpty() || espaces.isEmpty()) {
            return resultat;
        }
        Set<String> demandees = new HashSet<>(cles);
        for (ReferentielStandardsEnBase.TechnologieSuivie t : referentiel.technologies(espaces, true)) {
            if (demandees.contains(t.cle())) {
                resultat.computeIfAbsent(t.cle(), k -> new LinkedHashSet<>()).add(t.produitCycleVie());
            }
        }
        return resultat;
    }

    private Map<String, List<LigneCycle>> cycles(Collection<String> produits) {
        Map<String, List<LigneCycle>> resultat = new HashMap<>();
        if (produits.isEmpty()) {
            return resultat;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT produit, cycle, sortie_le, fin_support_le, derniere_version, derniere_version_le FROM cycle_vie
                WHERE produit = ANY (?) ORDER BY produit, cycle
                """)) {
            ps.setArray(1, c.createArrayOf("varchar", new LinkedHashSet<>(produits).toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LigneCycle l = new LigneCycle(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                            rs.getObject(4, LocalDate.class), rs.getString(5), rs.getObject(6, LocalDate.class));
                    resultat.computeIfAbsent(l.produit(), k -> new ArrayList<>()).add(l);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du calendrier impossible", e);
        }
        return resultat;
    }
}
