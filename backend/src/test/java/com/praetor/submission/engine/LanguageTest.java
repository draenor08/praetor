package com.praetor.submission.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The language table is data, not behaviour, so these assertions pin the parts other code depends
 * on: the {@code submissions.language} strings resolve, unknown ones are refused rather than
 * defaulted, and every entry carries a usable source file, compile step and run command.
 */
class LanguageTest {

    @Test
    void resolvesEverySupportedLanguage() {
        assertThat(Language.from("CPP")).isEqualTo(Language.CPP);
        assertThat(Language.from("PYTHON")).isEqualTo(Language.PYTHON);
        assertThat(Language.from("JAVA")).isEqualTo(Language.JAVA);
    }

    @Test
    void refusesUnknownAndMalformedNames() {
        assertThat(Language.from("RUST")).isNull();
        assertThat(Language.from("java")).isNull();
        assertThat(Language.from("")).isNull();
        assertThat(Language.from(null)).isNull();
    }

    /** javac derives the class name from the file name, so this pair must not drift apart. */
    @Test
    void javaCompilesAndRunsTheMainClass() {
        assertThat(Language.JAVA.sourceFile()).isEqualTo("Main.java");
        assertThat(Language.JAVA.compileCmd()).containsExactly(
                "javac", "-encoding", "UTF-8", "Main.java");
        assertThat(Language.JAVA.runCmd()).endsWith("Main");
    }

    @Test
    void everyLanguageIsFullySpecified() {
        for (Language language : Language.values()) {
            assertThat(language.sourceFile()).as("sourceFile of %s", language).isNotBlank();
            assertThat(language.compileCmd()).as("compileCmd of %s", language).isNotEmpty();
            assertThat(language.runCmd()).as("runCmd of %s", language).isNotEmpty();
            // Limits are written C++-first; anything slower gets headroom, nothing gets less.
            assertThat(language.timeMultiplier()).as("timeMultiplier of %s", language)
                    .isGreaterThanOrEqualTo(1.0);
            assertThat(language.memMultiplier()).as("memMultiplier of %s", language)
                    .isGreaterThanOrEqualTo(1.0);
        }
    }
}
