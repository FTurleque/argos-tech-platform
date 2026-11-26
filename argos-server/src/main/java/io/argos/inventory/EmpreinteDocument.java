package io.argos.inventory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Empreinte SHA-256, en hexadécimal, d'un document d'inventaire lu (INV-14) : version de lecture, nature du document
 * puis contenu. Pour des manifestes, le contenu est formé des chemins triés, chacun suivi de la longueur et des octets
 * de son contenu. Deux lectures de même empreinte donnent le même inventaire.
 */
final class EmpreinteDocument {

    /**
     * Version de l'analyse des documents : elle change quand l'analyse d'un même document change de résultat, pour que
     * l'import suivant réécrive l'inventaire.
     */
    static final int VERSION_LECTURE = 1;

    private static final String ALGORITHME = "SHA-256";
    private static final String SEPARATEUR = "|";

    private EmpreinteDocument() {
    }

    /** Empreinte d'une SBOM. */
    static String sbom(byte[] document) {
        MessageDigest calcul = nouveauCalcul("SBOM");
        calcul.update(document);
        return HexFormat.of().formatHex(calcul.digest());
    }

    /** Empreinte d'un jeu de manifestes (chemin → contenu), indépendante de l'ordre de lecture. */
    static String manifestes(Map<String, byte[]> manifestes) {
        MessageDigest calcul = nouveauCalcul("MANIFEST");
        for (Map.Entry<String, byte[]> manifeste : new TreeMap<>(manifestes).entrySet()) {
            calcul.update(manifeste.getKey().getBytes(StandardCharsets.UTF_8));
            calcul.update(ByteBuffer.allocate(Long.BYTES).putLong(manifeste.getValue().length).array());
            calcul.update(manifeste.getValue());
        }
        return HexFormat.of().formatHex(calcul.digest());
    }

    private static MessageDigest nouveauCalcul(String nature) {
        try {
            MessageDigest calcul = MessageDigest.getInstance(ALGORITHME);
            calcul.update((VERSION_LECTURE + SEPARATEUR + nature + SEPARATEUR).getBytes(StandardCharsets.UTF_8));
            return calcul;
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("algorithme " + ALGORITHME + " indisponible dans la JVM", absent);
        }
    }
}
