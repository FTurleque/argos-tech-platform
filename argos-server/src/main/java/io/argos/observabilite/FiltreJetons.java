package io.argos.observabilite;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import org.jboss.logmanager.ExtLogRecord;

import io.quarkus.logging.LoggingFilter;

/**
 * Filtre des journaux {@value #NOM} (EXP-13-S4) : remplace tout jeton JWT (trois segments Base64url séparés par des
 * points, dont l'en-tête commence par {@code eyJ}) par {@value #MASQUE} avant l'écriture, dans le message de
 * l'événement et dans les messages de l'exception journalisée et de ses causes. Il couvre les messages des extensions,
 * dont l'échec de vérification d'un jeton par OIDC qui cite le jeton reçu. Il est posé sur le journal de la console et
 * ne retire aucun événement.
 */
@LoggingFilter(name = FiltreJetons.NOM)
public final class FiltreJetons implements Filter {

    /** Nom du filtre dans {@code quarkus.log.console.filter}. */
    public static final String NOM = "masquage-jetons";

    /** Texte qui remplace un jeton dans un message. */
    static final String MASQUE = "[jeton masqué]";

    private static final Pattern JETON = Pattern.compile("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    /**
     * Exception substituée à une exception dont le message cite un jeton : même type nommé dans le message, même pile,
     * causes et exceptions supprimées substituées de même.
     */
    static final class ExceptionMasquee extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ExceptionMasquee(Throwable origine) {
            super(origine.getClass().getName() + ": " + masquer(String.valueOf(origine.getMessage())),
                    origine.getCause() == null ? null : masquerPile(origine.getCause()));
            setStackTrace(origine.getStackTrace());
            for (Throwable supprimee : origine.getSuppressed()) {
                addSuppressed(masquerPile(supprimee));
            }
        }
    }

    @Override
    public boolean isLoggable(LogRecord evenement) {
        if (evenement instanceof ExtLogRecord etendu) {
            String message = etendu.getFormattedMessage();
            if (message != null && JETON.matcher(message).find()) {
                etendu.setMessage(masquer(message), ExtLogRecord.FormatStyle.NO_FORMAT);
                etendu.setParameters(null);
            }
        }
        if (evenement.getThrown() != null) {
            evenement.setThrown(masquerPile(evenement.getThrown()));
        }
        return true;
    }

    /** Message dont chaque jeton est remplacé par {@value #MASQUE}. */
    static String masquer(String message) {
        return JETON.matcher(message).replaceAll(MASQUE);
    }

    /** L'exception elle-même si aucun message de sa pile ne cite de jeton, sinon sa copie masquée. */
    static Throwable masquerPile(Throwable erreur) {
        return citeUnJeton(erreur) ? new ExceptionMasquee(erreur) : erreur;
    }

    private static boolean citeUnJeton(Throwable erreur) {
        for (Throwable courante = erreur; courante != null; courante = courante.getCause()) {
            if (courante.getMessage() != null && JETON.matcher(courante.getMessage()).find()) {
                return true;
            }
            for (Throwable supprimee : courante.getSuppressed()) {
                if (citeUnJeton(supprimee)) {
                    return true;
                }
            }
        }
        return false;
    }
}
