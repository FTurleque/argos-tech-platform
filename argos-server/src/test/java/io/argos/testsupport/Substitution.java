package io.argos.testsupport;

import java.lang.reflect.Field;

import io.quarkus.arc.ClientProxy;

/**
 * Remplace, le temps d'un test, une dépendance injectée de l'instance réelle d'un bean CDI par une doublure, puis la
 * rétablit à la fermeture ({@code try (Substitution substitution = Substitution.remplacer(...))}). Sert à simuler une
 * erreur imprévue à une frontière d'erreur (arc42 §8.5) sans changer la configuration de l'application.
 */
public final class Substitution implements AutoCloseable {

    private final Object instance;
    private final Field champ;
    private final Object origine;

    private Substitution(Object instance, Field champ, Object origine) {
        this.instance = instance;
        this.champ = champ;
        this.origine = origine;
    }

    /**
     * Remplace le champ {@code nomChamp} de l'instance réelle derrière {@code bean} par {@code doublure}.
     *
     * @return la substitution, à fermer pour rétablir la dépendance d'origine
     */
    public static Substitution remplacer(Object bean, String nomChamp, Object doublure) {
        Object instance = ClientProxy.unwrap(bean);
        Field champ = champ(instance.getClass(), nomChamp);
        Object origine = lire(champ, instance);
        ecrire(champ, instance, doublure);
        return new Substitution(instance, champ, origine);
    }

    /** Instance réelle derrière un proxy client CDI, à laquelle une doublure délègue. */
    public static <T> T reel(T bean) {
        return ClientProxy.unwrap(bean);
    }

    @Override
    public void close() {
        ecrire(champ, instance, origine);
    }

    private static Field champ(Class<?> classe, String nomChamp) {
        for (Class<?> courante = classe; courante != null; courante = courante.getSuperclass()) {
            try {
                Field champ = courante.getDeclaredField(nomChamp);
                champ.setAccessible(true);
                return champ;
            } catch (NoSuchFieldException absent) {
                // Champ déclaré par une classe parente (sous-classe générée par ArC) : recherche poursuivie.
                continue;
            }
        }
        throw new IllegalArgumentException("champ " + nomChamp + " introuvable dans " + classe.getName());
    }

    private static Object lire(Field champ, Object instance) {
        try {
            return champ.get(instance);
        } catch (IllegalAccessException refus) {
            throw new IllegalStateException("lecture du champ " + champ.getName() + " refusée", refus);
        }
    }

    private static void ecrire(Field champ, Object instance, Object valeur) {
        try {
            champ.set(instance, valeur);
        } catch (IllegalAccessException refus) {
            throw new IllegalStateException("écriture du champ " + champ.getName() + " refusée", refus);
        }
    }
}
