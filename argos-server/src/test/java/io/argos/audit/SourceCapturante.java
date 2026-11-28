package io.argos.audit;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

/**
 * Source de données simulée qui capture les écritures d'un journal sans base : chaque instruction exécutée est gardée
 * avec son texte SQL et ses paramètres liés, par rang. Les valeurs sont donc celles que le journal a bornées ou
 * ramenées avant l'écriture.
 */
final class SourceCapturante {

    /** Instruction exécutée : texte SQL et paramètres liés, indexés par rang à partir de 1. */
    record Instruction(String sql, Map<Integer, Object> parametres) {

        Object parametre(int rang) {
            return parametres.get(rang);
        }
    }

    private final List<Instruction> instructions = Collections.synchronizedList(new ArrayList<>());
    private final DataSource source;

    SourceCapturante() {
        source = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (instance, methode, arguments) -> switch (methode.getName()) {
                    case "getConnection" -> connexion();
                    case "toString" -> "SourceCapturante";
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
    }

    DataSource source() {
        return source;
    }

    /** Instructions exécutées, dans l'ordre. */
    List<Instruction> instructions() {
        synchronized (instructions) {
            return List.copyOf(instructions);
        }
    }

    /** Seule instruction exécutée. */
    Instruction seule() {
        List<Instruction> executees = instructions();
        if (executees.size() != 1) {
            throw new AssertionError("une seule instruction attendue : " + executees);
        }
        return executees.get(0);
    }

    private Connection connexion() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (instance, methode, arguments) -> switch (methode.getName()) {
                    case "prepareStatement" -> instruction((String) arguments[0]);
                    case "close" -> null;
                    case "isClosed" -> false;
                    case "toString" -> "ConnexionCapturante";
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
    }

    private PreparedStatement instruction(String sql) {
        Map<Integer, Object> parametres = new TreeMap<>();
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (instance, methode, arguments) -> {
                    String nom = methode.getName();
                    if (nom.startsWith("set") && arguments != null && arguments.length >= 2 && arguments[0] instanceof Integer rang) {
                        parametres.put(rang, arguments[1]);
                        return null;
                    }
                    return switch (nom) {
                        case "executeUpdate" -> {
                            instructions.add(new Instruction(sql, Collections.unmodifiableMap(new TreeMap<>(parametres))));
                            yield 1;
                        }
                        case "close" -> null;
                        case "toString" -> "InstructionCapturante";
                        default -> throw new UnsupportedOperationException(nom);
                    };
                });
    }
}
