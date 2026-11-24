package io.argos.observabilite;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import org.jboss.logmanager.ExtLogRecord;

import io.quarkus.logging.LoggingFilter;

/**
 * Filtre des journaux {@value #NOM} (EXP-13-S4) : remplace tout jeton JWT (trois segments Base64url séparés par des
 * points, dont l'en-tête commence par {@code eyJ}) par {@value #MASQUE} avant l'écriture. Il couvre les messages des
 * extensions, dont l'échec de vérification d'un jeton par OIDC qui cite le jeton reçu. Il est posé sur le journal de
 * la console et ne retire aucun événement.
 */
@LoggingFilter(name = FiltreJetons.NOM)
public final class FiltreJetons implements Filter {

    /** Nom du filtre dans {@code quarkus.log.console.filter}. */
    public static final String NOM = "masquage-jetons";

    /** Texte qui remplace un jeton dans un message. */
    static final String MASQUE = "[jeton masqué]";

    private static final Pattern JETON = Pattern.compile("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    @Override
    public boolean isLoggable(LogRecord evenement) {
        if (evenement instanceof ExtLogRecord etendu) {
            String message = etendu.getFormattedMessage();
            if (message != null && JETON.matcher(message).find()) {
                etendu.setMessage(masquer(message), ExtLogRecord.FormatStyle.NO_FORMAT);
                etendu.setParameters(null);
            }
        }
        return true;
    }

    /** Message dont chaque jeton est remplacé par {@value #MASQUE}. */
    static String masquer(String message) {
        return JETON.matcher(message).replaceAll(MASQUE);
    }
}
