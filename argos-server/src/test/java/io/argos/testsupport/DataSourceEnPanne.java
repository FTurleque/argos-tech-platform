package io.argos.testsupport;

import java.lang.reflect.Proxy;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Source de données dont chaque demande de connexion échoue : simule une base injoignable pour éprouver
 * les chemins d'échec d'un composant instancié hors du conteneur, sans toucher à la base des tests.
 */
public final class DataSourceEnPanne {

    /** État SQL d'une connexion impossible (classe 08, « connection exception »). */
    public static final String ETAT_CONNEXION_IMPOSSIBLE = "08001";

    private DataSourceEnPanne() {
    }

    public static DataSource creer() {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (instance, methode, arguments) -> {
                    if (methode.getName().startsWith("getConnection")) {
                        throw new SQLException("base simulée injoignable", ETAT_CONNEXION_IMPOSSIBLE);
                    }
                    if ("toString".equals(methode.getName())) {
                        return "DataSourceEnPanne";
                    }
                    throw new UnsupportedOperationException(methode.getName());
                });
    }
}
