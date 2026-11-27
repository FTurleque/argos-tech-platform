package io.argos.testsupport;

import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Exécution sans Docker (AUD-TST-03) : sous le profil Maven {@code sans-docker}, les classes {@link QuarkusTest} et
 * {@link QuarkusIntegrationTest}, qui démarrent PostgreSQL et Keycloak par les Dev Services, sont écartées ; les tests
 * unitaires et d'architecture s'exécutent seuls. Le profil active la détection automatique des extensions JUnit et
 * pose la propriété système {@value #PROPRIETE} ; sans elle, cette condition laisse tout s'exécuter.
 */
public class TestsSansDocker implements ExecutionCondition {

    /** Propriété système qui écarte les tests exigeant Docker quand elle vaut {@code true}. */
    public static final String PROPRIETE = "argos.tests.sans-docker";

    private static final ConditionEvaluationResult ADMIS =
            ConditionEvaluationResult.enabled("test exécutable sans Docker");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext contexte) {
        if (!Boolean.getBoolean(PROPRIETE)) {
            return ADMIS;
        }
        Optional<Class<?>> classe = contexte.getTestClass();
        boolean exigeDocker = classe.isPresent()
                && (AnnotationSupport.isAnnotated(classe.get(), QuarkusTest.class)
                        || AnnotationSupport.isAnnotated(classe.get(), QuarkusIntegrationTest.class));
        return exigeDocker
                ? ConditionEvaluationResult.disabled("profil sans-docker : test Quarkus exigeant les Dev Services")
                : ADMIS;
    }
}
