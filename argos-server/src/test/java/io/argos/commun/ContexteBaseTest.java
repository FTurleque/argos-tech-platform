package io.argos.commun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Invariant NC-4 : tout contexte appliqué pose le rôle {@code argos_app} puis les quatre paramètres ; un paramètre
 * absent vaut la chaîne vide, jamais une exception (ADR-0037).
 */
class ContexteBaseTest {

    private static final UUID COMPTE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ESPACE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Instructions et paramètres reçus par la fausse connexion. */
    private static final class Journal {
        final List<String> instructions = new ArrayList<>();
        final Map<Integer, String> parametres = new java.util.TreeMap<>();
    }

    private static Connection fausseConnexion(Journal journal) {
        InvocationHandler requete = (proxy, methode, arguments) -> {
            if ("setString".equals(methode.getName())) {
                journal.parametres.put((Integer) arguments[0], (String) arguments[1]);
            }
            if ("close".equals(methode.getName())) {
                return null;
            }
            return "execute".equals(methode.getName()) ? Boolean.TRUE : null;
        };
        InvocationHandler instruction = (proxy, methode, arguments) -> {
            if ("execute".equals(methode.getName())) {
                journal.instructions.add((String) arguments[0]);
                return Boolean.TRUE;
            }
            return null;
        };
        InvocationHandler connexion = (proxy, methode, arguments) -> {
            if ("createStatement".equals(methode.getName())) {
                return Proxy.newProxyInstance(ContexteBaseTest.class.getClassLoader(),
                        new Class<?>[] {Statement.class}, instruction);
            }
            if ("prepareStatement".equals(methode.getName())) {
                journal.instructions.add((String) arguments[0]);
                return Proxy.newProxyInstance(ContexteBaseTest.class.getClassLoader(),
                        new Class<?>[] {PreparedStatement.class}, requete);
            }
            return null;
        };
        return (Connection) Proxy.newProxyInstance(ContexteBaseTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, connexion);
    }

    @Test
    void lesQuatreParametresSontPoses() throws SQLException {
        Journal journal = new Journal();
        ContexteBase.appliquer(fausseConnexion(journal),
                new ContexteBase.Contexte(COMPTE, "sujet-1", "a@exemple.test", ESPACE));
        assertEquals("SET LOCAL ROLE argos_app", journal.instructions.get(0));
        assertTrue(journal.instructions.get(1).contains("argos.compte_courant"));
        assertTrue(journal.instructions.get(1).contains("argos.sujet_courant"));
        assertTrue(journal.instructions.get(1).contains("argos.email_courant"));
        assertTrue(journal.instructions.get(1).contains("argos.espace_courant"));
        assertEquals(Map.of(1, COMPTE.toString(), 2, "sujet-1", 3, "a@exemple.test", 4, ESPACE.toString()),
                journal.parametres);
    }

    @Test
    void unParametreAbsentVautLaChaineVide() throws SQLException {
        Journal journal = new Journal();
        ContexteBase.appliquer(fausseConnexion(journal), ContexteBase.Contexte.duCompte(null));
        assertEquals(Map.of(1, "", 2, "", 3, "", 4, ""), journal.parametres);
    }

    @Test
    void lContexteDUnCompteNePoseNiSujetNiCourrielNiEspace() throws SQLException {
        Journal journal = new Journal();
        ContexteBase.appliquer(fausseConnexion(journal), ContexteBase.Contexte.duCompte(COMPTE));
        assertEquals(Map.of(1, COMPTE.toString(), 2, "", 3, "", 4, ""), journal.parametres);
    }
}
