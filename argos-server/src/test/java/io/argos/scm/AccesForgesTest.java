package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Configuration commune des forges : adresses normalisées, jetons blancs ignorés et jamais affichés. */
class AccesForgesTest {

    private static final Duration DELAI = Duration.ofSeconds(1);

    @Test
    void barreFinaleRetireeDesAdresses() {
        AccesForges acces = acces("https://gitlab.example/", "https://api.github.example/", Optional.empty(), Optional.empty());
        assertEquals("https://gitlab.example", acces.gitlabUrl());
        assertEquals("https://api.github.example", acces.githubUrl());

        AccesForges sansBarre = acces("https://gitlab.example", "https://api.github.example", Optional.empty(), Optional.empty());
        assertEquals("https://gitlab.example", sansBarre.gitlabUrl());
        assertEquals("https://api.github.example", sansBarre.githubUrl());
    }

    @Test
    void jetonAbsentVideOuBlancValantAbsence() {
        AccesForges absents = acces("https://g", "https://h", Optional.empty(), Optional.empty());
        assertEquals(Optional.empty(), absents.gitlabToken());
        assertEquals(Optional.empty(), absents.githubToken());

        AccesForges blancs = acces("https://g", "https://h", Optional.of(""), Optional.of("   "));
        assertEquals(Optional.empty(), blancs.gitlabToken());
        assertEquals(Optional.empty(), blancs.githubToken());

        AccesForges renseignes = acces("https://g", "https://h", Optional.of("jeton-gl"), Optional.of("jeton-gh"));
        assertEquals(Optional.of("jeton-gl"), renseignes.gitlabToken());
        assertEquals(Optional.of("jeton-gh"), renseignes.githubToken());
    }

    @Test
    void jetonsAbsentsDeLaDescription() {
        String description = acces("https://g", "https://h", Optional.of("secret-gl"), Optional.of("secret-gh")).toString();
        assertFalse(description.contains("secret-gl"), description);
        assertFalse(description.contains("secret-gh"), description);
    }

    @Test
    void forgeEnMajusculesEtVideSiAbsente() {
        assertEquals("GITLAB", AccesForges.provider(new RepositoryRef("gitlab", "g/p", "main", null, null)));
        assertEquals("GITHUB", AccesForges.provider(new RepositoryRef("GitHub", "o/r", "main", null, null)));
        assertEquals("", AccesForges.provider(new RepositoryRef(null, "o/r", "main", null, null)));
    }

    @Test
    void espaceEncodeEnPourcent20() {
        assertEquals("a%20b%2Fc", AccesForges.enc("a b/c"));
        assertEquals("%3E%3D2026-11-18", AccesForges.enc(">=2026-11-18"));
    }

    private static AccesForges acces(String gitlab, String github, Optional<String> jetonGitlab, Optional<String> jetonGithub) {
        return AccesForges.configure(new ParametresForges(DELAI, gitlab, github, jetonGitlab, jetonGithub), Clock.systemUTC());
    }
}
