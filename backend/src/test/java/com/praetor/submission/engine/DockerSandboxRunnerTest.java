package com.praetor.submission.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the two pieces of the sandbox that are pure string handling: the GNU-time elapsed
 * parser that TLE detection depends on, and the shell script the container-reuse path execs. No
 * docker here — the sandbox itself is verified live by the end-to-end verdict matrix.
 */
class DockerSandboxRunnerTest {

    @Test
    void parsesSecondsWithFraction() {
        assertThat(DockerSandboxRunner.parseElapsedMs("0:00.01")).isEqualTo(10);
        assertThat(DockerSandboxRunner.parseElapsedMs("0:01.50")).isEqualTo(1500);
    }

    @Test
    void parsesMinutesAndSeconds() {
        assertThat(DockerSandboxRunner.parseElapsedMs("1:02.5")).isEqualTo(62_500);
        assertThat(DockerSandboxRunner.parseElapsedMs("2:00")).isEqualTo(120_000);
    }

    @Test
    void parsesHoursMinutesSeconds() {
        assertThat(DockerSandboxRunner.parseElapsedMs("1:02:03")).isEqualTo(3_723_000);
    }

    @Test
    void parsesBareSeconds() {
        assertThat(DockerSandboxRunner.parseElapsedMs("0.25")).isEqualTo(250);
    }

    @Test
    void returnsMinusOneOnGarbage() {
        assertThat(DockerSandboxRunner.parseElapsedMs("n/a")).isEqualTo(-1);
    }

    // --- the exec script (container-reuse path) ---------------------------------------------

    @Test
    void scriptRecordsTheExitCodeSoAFailedExecIsDistinguishable() {
        String script = DockerSandboxRunner.shellScript(List.of("./prog"), "input.txt");
        assertThat(script).isEqualTo("'./prog' < 'input.txt' ; echo $? > exit.txt");
    }

    @Test
    void scriptWithoutStdinStillRecordsTheExitCode() {
        String script = DockerSandboxRunner.shellScript(List.of("g++", "-O2", "main.cpp"), null);
        assertThat(script).isEqualTo("'g++' '-O2' 'main.cpp' ; echo $? > exit.txt");
    }

    @Test
    void everyArgumentIsQuotedSoTheShellCannotResplitIt() {
        String script = DockerSandboxRunner.shellScript(List.of("java", "-cp", ".", "Main"), null);
        assertThat(script).contains("'-cp' '.'");
        // A space inside one argument must stay inside one argument.
        assertThat(DockerSandboxRunner.shellQuote("a b")).isEqualTo("'a b'");
    }

    @Test
    void singleQuotesInsideAnArgumentAreEscaped() {
        assertThat(DockerSandboxRunner.shellQuote("it's")).isEqualTo("'it'\\''s'");
    }
}
