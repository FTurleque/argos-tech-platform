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
 * puis contenu. Pour des manifestes, le contenu est formé des chemins triés, chacun précédé de sa longueur et suivi
 * de la longueur et des octets de son contenu. Deux lectures de même empreinte donnent le même inventaire.
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
        MessageDigest condensat = nouveauCondensat("SBOM");
        condensat.update(document);
        return HexFormat.of().formatHex(condensat.digest());
    }

    /** Empreinte d'un jeu de manifestes (chemin → contenu), indépendante de l'ordre de lecture. */
    static String manifestes(Map<String, byte[]> manifestes) {
        MessageDigest condensat = nouveauCondensat("MANIFEST");
        for (Map.Entry<String, byte[]> manifeste : new TreeMap<>(manifestes).entrySet()) {
            byte[] chemin = manifeste.getKey().getBytes(StandardCharsets.UTF_8);
            condensat.update(ByteBuffer.allocate(Integer.BYTES).putInt(chemin.length).array());
            condensat.update(chemin);
            condensat.update(ByteBuffer.allocate(Long.BYTES).putLong(manifeste.getValue().length).array());
            condensat.update(manifeste.getValue());
        }
        return HexFormat.of().formatHex(condensat.digest());
    }

    private static MessageDigest nouveauCondensat(String nature) {
        try {
            MessageDigest condensat = MessageDigest.getInstance(ALGORITHME);
            condensat.update((VERSION_LECTURE + SEPARATEUR + nature + SEPARATEUR).getBytes(StandardCharsets.UTF_8));
            return condensat;
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("algorithme " + ALGORITHME + " indisponible dans la JVM", absent);
        }
    }
}
