package io.argos.ingestion.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Résolveur DNS de test qui simule un rebinding : pour chaque nom, le n-ième appel renvoie la n-ième adresse de sa
 * liste (la dernière ensuite) ; une adresse absente ({@link Optional#empty()}) fait échouer l'appel. Compte les
 * appels par nom.
 */
final class ResolveurChangeant implements GardeAdresses.Resolveur {

    private final Map<String, List<Optional<String>>> reponses;
    private final Map<String, Integer> appels = new ConcurrentHashMap<>();

    private ResolveurChangeant(Map<String, List<Optional<String>>> reponses) {
        this.reponses = reponses;
    }

    /** Résolveur dont chaque nom reçoit ses adresses successives, toutes présentes. */
    static ResolveurChangeant successives(Map<String, List<String>> adresses) {
        Map<String, List<Optional<String>>> reponses = new ConcurrentHashMap<>();
        adresses.forEach((hote, liste) -> reponses.put(hote, liste.stream().map(Optional::of).toList()));
        return new ResolveurChangeant(reponses);
    }

    /** Résolveur qui répond {@code adresse} au premier appel pour {@code hote}, puis échoue à chaque appel suivant. */
    static ResolveurChangeant premierAppelSeulement(String hote, String adresse) {
        return new ResolveurChangeant(Map.of(hote, List.of(Optional.of(adresse), Optional.empty())));
    }

    /** Nombre d'appels reçus pour un nom. */
    int appels(String hote) {
        return appels.getOrDefault(hote, 0);
    }

    @Override
    public InetAddress[] resoudre(String hote) throws UnknownHostException {
        int rang = appels.merge(hote, 1, Integer::sum) - 1;
        List<Optional<String>> successives = reponses.get(hote);
        if (successives == null) {
            throw new UnknownHostException(hote);
        }
        Optional<String> adresse = successives.get(Math.min(rang, successives.size() - 1));
        if (adresse.isEmpty()) {
            throw new UnknownHostException(hote + " (appel " + (rang + 1) + ")");
        }
        return new InetAddress[] { InetAddress.getByName(adresse.get()) };
    }
}
