package io.argos.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture de {@code application.properties} sans démarrer Quarkus : valeur brute d'une propriété et valeur par défaut
 * d'une expression {@code ${VARIABLE:defaut}}, celle qu'obtient le serveur quand la variable d'environnement est absente.
 */
public final class ProprietesApplication {

    private static final String RESSOURCE = "application.properties";

    /** Expression entière {@code ${VARIABLE:defaut}} ; le groupe 1 est la valeur par défaut. */
    private static final Pattern EXPRESSION = Pattern.compile("^\\$\\{[A-Za-z0-9_.]+:(.*)\\}$");

    private static final Properties PROPRIETES = charger();

    private ProprietesApplication() {
    }

    private static Properties charger() {
        try (InputStream flux = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESSOURCE)) {
            if (flux == null) {
                throw new IllegalStateException(RESSOURCE + " introuvable dans le chemin de classes");
            }
            Properties proprietes = new Properties();
            try (Reader lecteur = new InputStreamReader(flux, StandardCharsets.UTF_8)) {
                proprietes.load(lecteur);
            }
            return proprietes;
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    /**
     * Valeur brute de la propriété (préfixe de profil compris dans la clé, par exemple {@code %test.}).
     *
     * @throws IllegalArgumentException si la propriété est absente
     */
    public static String brute(String cle) {
        String valeur = PROPRIETES.getProperty(cle);
        if (valeur == null) {
            throw new IllegalArgumentException("propriété absente de " + RESSOURCE + " : " + cle);
        }
        return valeur.strip();
    }

    /** Valeur par défaut de la propriété : celle de son expression {@code ${VARIABLE:defaut}}, sinon sa valeur brute. */
    public static String parDefaut(String cle) {
        String valeur = brute(cle);
        Matcher expression = EXPRESSION.matcher(valeur);
        return expression.matches() ? expression.group(1) : valeur;
    }
}
