package io.argos.scm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

/**
 * SBOM lue dans un dépôt : octets bruts et format reconnu. Le format est identifié sur l'en-tête du
 * document sans l'interpréter ; la validation du contenu appartient au module {@code inventory}
 * (INV-01, INV-05). Aucun objet propre à GitLab ou GitHub n'y figure.
 *
 * @param contenu octets du document, tels que servis par la forge
 * @param format format reconnu
 */
public record SbomDocument(byte[] contenu, Format format) {

    /** Formats de SBOM reconnus à la récupération. */
    public enum Format {
        /** CycloneDX en JSON ({@code "bomFormat": "CycloneDX"}). */
        CYCLONEDX_JSON("CycloneDX JSON"),
        /** CycloneDX en XML (espace de noms {@code http://cyclonedx.org/schema/bom/...}). */
        CYCLONEDX_XML("CycloneDX XML"),
        /** SPDX en JSON ({@code "spdxVersion"}), y compris l'export du graphe de dépendances GitHub ({@code {"sbom": {...}}}). */
        SPDX_JSON("SPDX JSON"),
        /** SPDX au format étiquette-valeur ({@code SPDXVersion: SPDX-2.3}). */
        SPDX_TAG_VALUE("SPDX tag-value"),
        /** Aucun des formats précédents. */
        INCONNU("format inconnu");

        private final String libelle;

        Format(String libelle) {
            this.libelle = libelle;
        }

        /** Libellé lisible, repris dans les motifs de refus. */
        public String libelle() {
            return libelle;
        }
    }

    /** Nombre d'octets examinés pour reconnaître un document XML ou étiquette-valeur. */
    private static final int ENTETE = 4096;

    /** Nombre maximal de champs de premier niveau parcourus dans un document JSON. */
    private static final int CHAMPS_MAX = 64;

    private static final JsonFactory JSON = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).build())
            .build();

    /** Document dont le format est reconnu à partir de son en-tête. */
    public static SbomDocument de(byte[] contenu) {
        return new SbomDocument(contenu, reconnaitre(contenu));
    }

    /** Format du document ; {@link Format#INCONNU} si aucun format n'est reconnu. */
    static Format reconnaitre(byte[] contenu) {
        int debut = debutUtile(contenu);
        if (debut >= contenu.length) {
            return Format.INCONNU;
        }
        byte premier = contenu[debut];
        if (premier == '{') {
            return json(contenu);
        }
        String entete = new String(contenu, debut, Math.min(ENTETE, contenu.length - debut), StandardCharsets.UTF_8);
        if (premier == '<') {
            return entete.contains("http://cyclonedx.org/schema/bom/") ? Format.CYCLONEDX_XML : Format.INCONNU;
        }
        return entete.startsWith("SPDXVersion:") ? Format.SPDX_TAG_VALUE : Format.INCONNU;
    }

    /** Position du premier octet utile : marque d'ordre des octets UTF-8 et blancs ignorés. */
    private static int debutUtile(byte[] contenu) {
        int i = 0;
        if (contenu.length >= 3 && (contenu[0] & 0xFF) == 0xEF && (contenu[1] & 0xFF) == 0xBB && (contenu[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < contenu.length && Character.isWhitespace(contenu[i])) {
            i++;
        }
        return i;
    }

    /**
     * Parcourt les champs de premier niveau sans construire l'arbre : {@code bomFormat} vaut
     * {@code CycloneDX}, {@code spdxVersion} est présent, ou {@code sbom} contient {@code spdxVersion}.
     */
    private static Format json(byte[] contenu) {
        try (JsonParser p = JSON.createParser(contenu)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                return Format.INCONNU;
            }
            for (int champs = 0; champs < CHAMPS_MAX && p.nextToken() == JsonToken.FIELD_NAME; champs++) {
                String nom = p.currentName();
                JsonToken valeur = p.nextToken();
                if ("bomFormat".equals(nom) && valeur == JsonToken.VALUE_STRING && "CycloneDX".equals(p.getText())) {
                    return Format.CYCLONEDX_JSON;
                }
                if ("spdxVersion".equals(nom)) {
                    return Format.SPDX_JSON;
                }
                if ("sbom".equals(nom) && valeur == JsonToken.START_OBJECT && contientSpdx(p)) {
                    return Format.SPDX_JSON;
                }
                p.skipChildren();
            }
            return Format.INCONNU;
        } catch (IOException e) {
            return Format.INCONNU;
        }
    }

    private static boolean contientSpdx(JsonParser p) throws IOException {
        for (int champs = 0; champs < CHAMPS_MAX && p.nextToken() == JsonToken.FIELD_NAME; champs++) {
            if ("spdxVersion".equals(p.currentName())) {
                return true;
            }
            p.nextToken();
            p.skipChildren();
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SbomDocument d && format == d.format && Arrays.equals(contenu, d.contenu);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(contenu) + format.hashCode();
    }

    @Override
    public String toString() {
        return "SbomDocument[" + format.libelle() + ", " + contenu.length + " octets]";
    }
}
