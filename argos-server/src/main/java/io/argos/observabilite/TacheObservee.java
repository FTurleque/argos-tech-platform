package io.argos.observabilite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

/**
 * Méthode {@code @Scheduled} observée par {@link IntercepteurTaches} : identifiant de corrélation et nom de tâche
 * dans le MDC, durée et résultat mesurés, dernière exécution retenue pour le contrôle de santé (EXP-13-S2,
 * EXP-14-S1, EXP-15). Toute méthode planifiée la porte : le test d'architecture {@code TachesObserveesTest} le vérifie.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface TacheObservee {
}
